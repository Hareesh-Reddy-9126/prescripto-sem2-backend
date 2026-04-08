# Prescripto Production Deployment Guide

This repository contains:
- Backend: Spring Boot + MySQL (`backend/`)
- Frontend (patient app): React + Vite (`frontend/`)
- Admin panel: React + Vite (`frontend/admin/`)
- Pharmacist panel: React + Vite (`frontend/pharmacist/`)

This guide prepares deployment to:
- Backend on Render
- Frontend apps on Vercel

## 1) Backend deployment (Render)

### Service setup
1. Create a new Render Web Service.
2. Connect your repository.
3. Set root directory to `backend`.
4. Set build command:
   - `mvn clean package`
5. Set start command:
   - `java -jar target/backend-1.0.0.jar`

You can also deploy with Docker using `backend/Dockerfile`.

### Required backend environment variables
Set these in Render:
- `DB_URL`
- `DB_USER`
- `DB_PASSWORD`
- `JWT_SECRET`

Optional but recommended:
- `ALLOWED_ORIGINS`
- `ADMIN_EMAIL`
- `ADMIN_PASSWORD`
- `CLOUDINARY_NAME`
- `CLOUDINARY_API_KEY`
- `CLOUDINARY_SECRET_KEY`
- `STRIPE_SECRET_KEY`
- `RAZORPAY_KEY_ID`
- `RAZORPAY_KEY_SECRET`

Notes:
- `PORT` is provided by Render automatically.
- `spring.jpa.hibernate.ddl-auto=update` is enabled.
- CORS is configured for localhost and Vercel domains by default, and can be overridden via `ALLOWED_ORIGINS`.

Example `ALLOWED_ORIGINS`:
- `https://your-main-app.vercel.app,https://your-admin-app.vercel.app,https://your-pharmacist-app.vercel.app,http://localhost:5173,http://localhost:5174,http://localhost:5175`

## 2) Frontend deployment (Vercel)

Deploy each app as a separate Vercel project.

### A) Patient frontend (`frontend/`)
- Root directory: `frontend`
- Framework preset: `Vite`
- Build command: `npm run build`
- Output directory: `dist`

Required environment variables:
- `VITE_API_URL=https://<your-render-backend-domain>`

Optional navigation URLs (for role switching):
- `VITE_ADMIN_URL=https://<your-admin-vercel-domain>`
- `VITE_DOCTOR_URL=https://<doctor-entry-url-if-separated>`
- `VITE_PHARMACIST_URL=https://<your-pharmacist-vercel-domain>`
- `VITE_FRONTEND_URL=https://<your-main-frontend-vercel-domain>`

### B) Admin frontend (`frontend/admin/`)
- Root directory: `frontend/admin`
- Framework preset: `Vite`
- Build command: `npm run build`
- Output directory: `dist`

Required environment variable:
- `VITE_API_URL=https://<your-render-backend-domain>`

### C) Pharmacist frontend (`frontend/pharmacist/`)
- Root directory: `frontend/pharmacist`
- Framework preset: `Vite`
- Build command: `npm run build`
- Output directory: `dist`

Required environment variable:
- `VITE_API_URL=https://<your-render-backend-domain>`

## 3) Local production-like verification

### Backend
From `backend/`, provide environment variables, then run:
- `mvn clean package`
- `java -jar target/backend-1.0.0.jar`

### Frontend apps
In each frontend app directory (`frontend/`, `frontend/admin/`, `frontend/pharmacist/`):
1. Create `.env.local`
2. Set:
   - `VITE_API_URL=http://localhost:4000`
3. Build:
   - `npm run build`

## 4) Final readiness checklist

- Backend uses environment variables for DB and JWT secret.
- No hardcoded backend localhost URL in frontend source configuration.
- All frontend apps build successfully.
- Backend JAR is generated in `backend/target/`.
- CORS supports localhost and Vercel production domains.
- Render and Vercel environment variables are configured before go-live.
