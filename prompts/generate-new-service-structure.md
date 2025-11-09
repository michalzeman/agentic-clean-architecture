For the {{Ask user for the service}} generate me basic submodule structure like is for the data-job-orchestrator.

Basic Structure is:
{{user input}}-domain - Core business logic
{{user input}}-application - Application services
{{user input}}-adapter-redis - Redis DB adapter
{{user input}}-adapter-redis-stream - Redis streams adapter
{{user input}}-boot-app - Spring Boot application

For the modules add build.gradle.kts files and also add packages without any code or classes.
Generate only spring boot class with application.yml file and make the service runnable