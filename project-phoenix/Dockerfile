# Use a base image with Java
FROM ibm-semeru-runtimes:open-17-jre-focal
ENV PORT 8080
EXPOSE 8080
ENV _JAVA_OPTIONS="-XX:MaxRAM=70m"

# Copy the built jar file into the image
COPY target/project-phoenix-1.0.1-SNAPSHOT.jar app.jar

# Set the entry point to run your application
CMD java -XX:MaxRAM=70m -jar /app/app.jar