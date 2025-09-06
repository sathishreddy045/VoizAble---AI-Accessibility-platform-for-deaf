import whisper
import uvicorn
import os
import uuid
import shutil
from fastapi import FastAPI, UploadFile, File, HTTPException

# --- 1. Create a local directory for uploads ---
UPLOADS_DIR = "uploads"
os.makedirs(UPLOADS_DIR, exist_ok=True)


# --- 2. Model Loading ---
print("Loading Whisper AI model... (This may take a moment)")
try:
    model = whisper.load_model("base")
    print("Whisper model loaded successfully.")
except Exception as e:
    print(f"Error loading Whisper model: {e}")
    exit()

# --- 3. FastAPI Application Setup ---
app = FastAPI(
    title="Voizable AI Transcription Service",
    description="A service to transcribe audio files and generate SRT captions using OpenAI's Whisper.",
    version="1.2.0" # Version updated
)

# --- 4. SRT Generation Logic (No changes here) ---
def generate_srt_from_segments(segments):
    srt_content = []
    for i, segment in enumerate(segments):
        start_time = segment['start']
        end_time = segment['end']
        text = segment['text'].strip()

        start_h, rem = divmod(start_time, 3600)
        start_m, start_s = divmod(rem, 60)
        start_ms = int((start_s - int(start_s)) * 1000)
        start_formatted = f"{int(start_h):02}:{int(start_m):02}:{int(start_s):02},{start_ms:03}"

        end_h, rem = divmod(end_time, 3600)
        end_m, end_s = divmod(rem, 60)
        end_ms = int((end_s - int(end_s)) * 1000)
        end_formatted = f"{int(end_h):02}:{int(end_m):02}:{int(end_s):02},{end_ms:03}"

        srt_content.append(str(i + 1))
        srt_content.append(f"{start_formatted} --> {end_formatted}")
        srt_content.append(text)
        srt_content.append("")

    return "\n".join(srt_content)

# --- 5. API Endpoints ---
@app.get("/", tags=["Health Check"])
def health_check():
    return {"status": "OK", "message": "Whisper AI service is active."}

@app.post("/transcribe", tags=["Transcription"])
async def transcribe_audio(file: UploadFile = File(..., description="Audio or video file to be transcribed.")):
    if not file:
        raise HTTPException(status_code=400, detail="No file was uploaded.")

    # Generate a unique filename to prevent conflicts
    unique_filename = str(uuid.uuid4()) + "_" + file.filename
    file_path = os.path.join(UPLOADS_DIR, unique_filename)

    try:
        # --- FIX: Write file to disk in chunks for robustness ---
        # This ensures the file is fully saved before we proceed.
        with open(file_path, "wb") as buffer:
            shutil.copyfileobj(file.file, buffer)

        # --- Core Transcription Logic ---
        # Now that the file is guaranteed to be complete, we can transcribe it.
        result = model.transcribe(file_path, fp16=False)

        # Generate SRT content
        srt_content = generate_srt_from_segments(result["segments"])

    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Error during transcription process: {str(e)}")

    finally:
        # --- Cleanup: Ensure the temporary file is always deleted ---
        if os.path.exists(file_path):
            os.remove(file_path)

    # Return a successful response
    return {
        "plain_text": result["text"],
        "srt_content": srt_content
    }