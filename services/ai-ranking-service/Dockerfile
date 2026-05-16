FROM python:3.11-slim
WORKDIR /app
COPY services/ai-ranking-service/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY services/ai-ranking-service/app ./app
EXPOSE 8084
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8084"]
