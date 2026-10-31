# KEEN ERP backend

Spring Boot API with PostgreSQL, Flyway migrations, session authentication, and CSRF protection. This folder is self-contained and can be uploaded to its own GitHub repository.

## Local development

Use Java 17 or newer and Maven 3.9+. The production Docker image builds and runs with Java 21.

1. Create a local PostgreSQL database named `fashion_pos`.
2. Copy `.env.example` to `.env` and set the database and admin credentials.
3. Run `mvn spring-boot:run`.

The API listens on `http://localhost:8080`. Check `/actuator/health` for readiness. Apply schema changes through the checked-in Flyway migrations in `src/main/resources/db/migration/`.

Validate before deploying:

```powershell
mvn -B -ntp package
```

## Render deployment

1. Push this folder's repository to GitHub, including `Dockerfile`, `.dockerignore`, `pom.xml`, `src/`, and `render.yaml`.
2. In Render, create a Blueprint from the backend repository. Use `render.yaml` at the repository root. Leave Root Directory empty because this repository already contains the backend at its root.
3. Provide the values requested by the Blueprint:

   ```text
   FASHIONPOS_ADMIN_EMAIL=<admin email>
   FASHIONPOS_ADMIN_PASSWORD=<strong admin password>
   FASHIONPOS_CORS_ALLOWED_ORIGINS=https://your-frontend.vercel.app
   ```

4. Deploy, then check `https://your-backend.onrender.com/actuator/health` for `{"status":"UP"}`.
5. Set `VITE_API_BASE_URL=https://your-backend.onrender.com` in the Vercel frontend project and redeploy it.
6. Once the frontend URL is assigned, update `FASHIONPOS_CORS_ALLOWED_ORIGINS` in Render to that exact origin, without a trailing slash.

The Blueprint creates PostgreSQL 16 and a Docker web service in the same region. It injects the private database connection string through `DATABASE_URL`; the application converts it into JDBC settings. `PORT` is supplied by Render. Flyway runs during startup. No manual build or start command is needed for the Docker service.

The Blueprint also sets `FASHIONPOS_SESSION_COOKIE_SECURE=true` and `FASHIONPOS_SESSION_COOKIE_SAME_SITE=None`. Browser requests include cookies and CSRF tokens. List exact frontend origins, separated by commas if necessary; credentialed CORS does not support `*`.

Some browsers block cookies between `vercel.app` and `onrender.com`. For reliable sessions, use frontend and API subdomains of the same owned domain, such as `pos.example.com` and `api.example.com`, and update the two URL settings accordingly.

### Service plans

The Blueprint retains free plans for an initial evaluation. Free Render web services sleep after 15 minutes without inbound traffic, and Free PostgreSQL expires after 30 days and has no backups. Choose paid service/database plans and arrange backups before storing live business data. See [Render's free-instance limits](https://render.com/docs/free).

### Optional integrations

Configure Resend and M-Pesa values from `.env.example` directly in Render's environment settings. M-Pesa stays disabled until `MPESA_ENABLED=true`; configure all payment credentials, callback security settings, and the public HTTPS `MPESA_CALLBACK_BASE_URL` before enabling it. Keep `MPESA_ENCRYPTION_KEY` stable across deployments so stored payment credentials remain readable. The Render Blueprint intentionally contains no payment or email secrets.

## GitHub contents

Keep application source, SQL migrations, tests, `scripts/`, Maven configuration, deployment files, and `.env.example` in Git. `.gitignore` excludes real environment files, private keys, database dumps, uploads, `target/`, logs, cookies, authentication payloads, and API debug responses. `.dockerignore` restricts the image build context to application inputs.

Before committing, review:

If this is a clean upload snapshot, first create a fresh repository with `git init -b main`. Follow the upload folder's top-level README to set a new GitHub remote.

```powershell
git status --short
git ls-files -ci --exclude-standard
git add --dry-run --all
```

The second command should print nothing: ignored files that were already tracked would still be included in future commits. Do not upload the entire local folder through GitHub's browser uploader; it does not apply Git ignore rules. Use Git to commit and push the reviewed files. If credentials were committed previously, ignoring them does not remove them from history; rotate them and clean that history before publishing.

Render references: [Blueprint specification](https://render.com/docs/blueprint-spec), [Docker deployments](https://render.com/docs/docker).
