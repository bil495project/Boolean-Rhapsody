# Developer Guide

## Prerequisites

| Tool | Version |
|------|---------|
| Java | 21+ |
| Maven | 3.9+ |
| Node.js | 18+ |
| PostgreSQL | 14+ |


## 1. Database Setup

1. Open a PostgreSQL client (e.g. `psql`, pgAdmin) and create the database:

```sql
CREATE DATABASE roadrunner;
```

2. Note down your PostgreSQL **username** and **password**.


## 2. Backend Setup

Route payload semantics and example requests:

- `backend/ROUTE_INPUT_GUIDE.md`

### 2.1 Configure `application.properties`

Copy the example file and fill in your credentials:

```bash
cd backend/src/main/resources
cp application.properties.example application.properties
```

Edit `application.properties` and fill in the blank values:

```properties
# Your PostgreSQL credentials
spring.datasource.username=YOUR_POSTGRES_USERNAME
spring.datasource.password=YOUR_POSTGRES_PASSWORD

# JWT secret — use a random Base64-encoded string (min 32 chars)
# You can generate one with openssl rand -base64 32
app.jwt.secret=YOUR_BASE64_SECRET_HERE

# reCAPTCHA v3 — get keys from https://www.google.com/recaptcha/admin
# Set to false during local development if you don't have keys
app.recaptcha.secret-key=YOUR_RECAPTCHA_SECRET_KEY
app.recaptcha.enabled=true
```

### 2.2 Run the Backend

```bash
cd backend
mvn spring-boot:run
```

The server starts at **http://localhost:8080**.

### 2.3 Run Tests

```bash
cd backend
mvn test
```

Tests use an H2 in-memory database. Configuration is in `src/test/resources/application-test.properties`.

## 3. Frontend Setup

### 3.1 Create Environment Files

Create `frontend/.env.development`:

```env
VITE_API_BASE_URL=http://localhost:8080/api
```

and also create `frontend/.env`:
```env
VITE_GEMINI_API_KEY=YOUR_GEMINI_API_KEY
VITE_GEMINI_MODEL=gemini-2.5-flash
```

| Variable | Description |
|----------|-------------|
| `VITE_API_BASE_URL` | Backend API URL. Default: `http://localhost:8080/api` |
| `VITE_GEMINI_API_KEY` | Google Gemini API key from [AI Studio](https://aistudio.google.com/apikey) |
| `VITE_GEMINI_MODEL` | *(Optional)* Gemini model name. Defaults to `gemini-2.5-flash` |

### 3.2 Install Dependencies & Run

```bash
cd frontend
npm install
npm run dev
```

The dev server starts at **http://localhost:5173**.

## 4. reCAPTCHA Configuration

- **Backend:** Set `app.recaptcha.secret-key` in `application.properties`
- **Frontend:** The site key is configured in `src/utils/recaptcha.ts`
- **Disable for dev:** Set `app.recaptcha.enabled=false` in `application.properties`

Get keys at: https://www.google.com/recaptcha/admin

## 5. Project Structure

```
bitirme/
├── backend/                    # Spring Boot API
│   ├── src/main/java/com/roadrunner/
│   │   ├── config/             # App configuration
│   │   ├── security/           # JWT, reCAPTCHA, auth filters
│   │   └── user/
│   │       ├── controller/     # REST controllers
│   │       ├── dto/            # Request/Response DTOs
│   │       ├── entity/         # JPA entities
│   │       ├── repository/     # Spring Data repositories
│   │       └── service/        # Business logic
│   └── src/test/               # Unit & integration tests
│
├── frontend/                   # React + Vite
│   ├── src/
│   │   ├── components/         # Reusable UI components
│   │   ├── pages/              # Route pages
│   │   ├── store/              # Redux slices & hooks
│   │   ├── services/           # API clients (userService, geminiService)
│   │   ├── data/               # Static data (destinations)
│   │   └── utils/              # Utilities (reCAPTCHA helpers)
│   └── .env.development        # Environment variables
│
└── DEVELOPER_GUIDE.md          # This file
```
