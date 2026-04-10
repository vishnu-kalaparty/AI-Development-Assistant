# Unified Dockerfile - builds backend (and frontend via Maven) into a single container

# Build stage: run Maven, which also builds the React frontend via frontend-maven-plugin
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

# Copy backend pom.xml and source
COPY backend/pom.xml ./
COPY backend/src ./src

# Copy frontend source so frontend-maven-plugin can build it
# Plugin is configured with workingDirectory ../frontend (i.e. /frontend from /app)
COPY frontend /frontend

# Build Spring Boot application (includes frontend build)
RUN mvn clean package -DskipTests

# Runtime stage: JDK + Python so sandbox run_code can execute .java and .py
FROM eclipse-temurin:17-jdk-jammy

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends python3 wget ca-certificates \
    && ln -sf /usr/bin/python3 /usr/local/bin/python \
    && rm -rf /var/lib/apt/lists/*

# Copy jar from build stage
COPY --from=build /app/target/*.jar app.jar

# Create directories for data
RUN mkdir -p /app/data/sandbox /app/data/personas /app/data/requirements

# Expose port 8080 (serves both API and React app)
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/api/health || exit 1

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]

