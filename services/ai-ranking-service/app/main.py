import os
import re
from collections import Counter

from fastapi import FastAPI
from pydantic import BaseModel, Field

try:
    from openai import OpenAI
except Exception:
    OpenAI = None

app = FastAPI(title="AI Ranking Service", version="0.1.0")


class RankRequest(BaseModel):
    jobDescription: str = Field(min_length=10)
    resumeText: str = Field(min_length=10)


class RankResponse(BaseModel):
    score: float
    matchedKeywords: list[str]
    explanation: str


def tokenize(text: str) -> list[str]:
    words = re.findall(r"[a-zA-Z][a-zA-Z0-9+#.-]{1,}", text.lower())
    stop_words = {
        "with", "from", "that", "this", "have", "will", "your", "their", "about",
        "into", "must", "able", "need", "using", "used", "years", "year", "work",
        "team", "role", "candidate", "experience", "required", "preferred"
    }
    return [w for w in words if w not in stop_words]


def keyword_score(job_description: str, resume_text: str) -> RankResponse:
    job_tokens = tokenize(job_description)
    resume_tokens = tokenize(resume_text)

    if not job_tokens:
        return RankResponse(score=0.0, matchedKeywords=[], explanation="No keywords found in job description")

    job_freq = Counter(job_tokens)
    resume_set = set(resume_tokens)

    matched = [kw for kw in job_freq.keys() if kw in resume_set]
    weighted_match = sum(job_freq[kw] for kw in matched)
    total_weight = sum(job_freq.values())

    score = round((weighted_match / total_weight) * 100, 2)
    top_matches = sorted(matched, key=lambda x: job_freq[x], reverse=True)[:12]

    explanation = f"Matched {len(top_matches)} important keywords from the job description"
    return RankResponse(score=score, matchedKeywords=top_matches, explanation=explanation)


def openai_score(job_description: str, resume_text: str) -> RankResponse | None:
    use_openai = os.getenv("USE_OPENAI", "false").lower() == "true"
    api_key = os.getenv("OPENAI_API_KEY", "")

    if not use_openai or not api_key or OpenAI is None:
        return None

    try:
        client = OpenAI(api_key=api_key)
        prompt = (
            "You are an ATS ranking engine. Return strict JSON with keys: score (0-100), "
            "matchedKeywords (array of strings), explanation (string).\n\n"
            f"JOB DESCRIPTION:\n{job_description}\n\n"
            f"RESUME:\n{resume_text[:8000]}"
        )
        completion = client.chat.completions.create(
            model="gpt-4o-mini",
            messages=[{"role": "user", "content": prompt}],
            response_format={"type": "json_object"},
            temperature=0.1,
        )
        content = completion.choices[0].message.content or "{}"
        import json

        parsed = json.loads(content)
        score = float(parsed.get("score", 0.0))
        keywords = parsed.get("matchedKeywords", [])
        explanation = parsed.get("explanation", "")
        return RankResponse(score=max(0.0, min(100.0, score)), matchedKeywords=keywords[:15], explanation=explanation)
    except Exception:
        return None


@app.get("/health")
def health() -> dict:
    return {"status": "ok"}


@app.post("/rank", response_model=RankResponse)
def rank(req: RankRequest) -> RankResponse:
    ai_result = openai_score(req.jobDescription, req.resumeText)
    if ai_result:
        return ai_result
    return keyword_score(req.jobDescription, req.resumeText)
