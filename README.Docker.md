### Building and running your application

When you're ready, start your application by running:
`docker compose up --build`.

Your application will be available at http://localhost:8080.

### Redis for rate limiting

Rate limiting is backed by Redis in this project. The `compose.yaml` now includes a `redis` service and wires the backend to it via:
- `SPRING_DATA_REDIS_HOST=redis`
- `SPRING_DATA_REDIS_PORT=6379`
- `APP_RATE_LIMIT_ENABLED=true`

If your Redis instance requires auth, set `SPRING_DATA_REDIS_PASSWORD` in your `.env`.

### Docker DB variables

Compose uses Docker-specific database variables (so local `.env` values like `localhost` do not break container networking):
- `DOCKER_SPRING_DATASOURCE_URL` (default: `jdbc:postgresql://db:5432/fileshare`)
- `DOCKER_SPRING_DATASOURCE_DRIVER_CLASS_NAME` (default: `org.postgresql.Driver`)
- `DOCKER_SPRING_DATASOURCE_USERNAME` (default: `postgres`)
- `DOCKER_SPRING_DATASOURCE_PASSWORD` (default: `postgres`)

### Optional Caddy

The `caddy` service is now under profile `edge`.
- Run app without caddy: `docker compose up --build`
- Run with caddy: `docker compose --profile edge up --build`

### Deploying your application to the cloud

First, build your image, e.g.: `docker build -t myapp .`.
If your cloud uses a different CPU architecture than your development
machine (e.g., you are on a Mac M1 and your cloud provider is amd64),
you'll want to build the image for that platform, e.g.:
`docker build --platform=linux/amd64 -t myapp .`.

Then, push it to your registry, e.g. `docker push myregistry.com/myapp`.

Consult Docker's [getting started](https://docs.docker.com/go/get-started-sharing/)
docs for more detail on building and pushing.
