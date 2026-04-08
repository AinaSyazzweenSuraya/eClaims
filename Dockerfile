# Use Java 17 lightweight image
FROM eclipse-temurin:17-jdk-alpine

# Set working directory inside container
WORKDIR /app

# Copy your JAR into the container
COPY target/eclaims-6.0.0.jar app.jar

# Make Spring Boot use the PORT variable from Render
ENV JAVA_OPTS=""

# Run the JAR
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]