FROM maven:3.9.9-eclipse-temurin-17 AS backend-build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM python:3.11-slim AS runtime
ENV DEBIAN_FRONTEND=noninteractive \
    NIRIKSHAN_PYTHON=/usr/local/bin/python3 \
    NIRIKSHAN_CV_PIPELINE_DIR=/app/cv-pipeline \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

RUN apt-get update \
    && apt-get install -y --no-install-recommends openjdk-21-jre-headless ffmpeg libglib2.0-0 libgl1 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=backend-build /build/target/nirikshan-0.0.1-SNAPSHOT.jar /app/nirikshan.jar
COPY cv-pipeline /app/cv-pipeline

RUN python -m pip install --no-cache-dir --upgrade pip \
    && python -m pip install --no-cache-dir --index-url https://download.pytorch.org/whl/cpu torch torchvision \
    && python -m pip install --no-cache-dir -r /app/cv-pipeline/requirements-railway.txt \
    && cd /app/cv-pipeline \
    && python -c "from ultralytics import YOLO; YOLO('yolo26s.pt')"

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=35", "-jar", "/app/nirikshan.jar"]
