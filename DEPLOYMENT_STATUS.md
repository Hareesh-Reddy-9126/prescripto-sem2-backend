# Deployment Status (Persistent)

Use this file to continue deployment later without repeating steps.

## Current Progress

- [x] Step 1: Railway MySQL created
- [x] Step 2: Render backend deployed
- [x] Step 3: Vercel main frontend deployed
- [x] Step 4: Vercel admin frontend deployed
- [x] Step 5: Vercel pharmacist frontend deployed
- [x] Step 6: CORS updated with final Vercel domains
- [x] Step 7: End-to-end smoke verification

Current blocker:

- Render build failed because Root Directory was set to `backend` but that folder does not exist in the connected GitHub repository.
- Immediate fix: set Root Directory to blank (or `.`) and redeploy.

Latest blocker:

- Render is building commit `8f40435` with an old Dockerfile that contains `COPY migration-backup ./migration-backup`.
- Build fails because `migration-backup` is not present in the GitHub repository.
- Immediate fix: push the updated Dockerfile from local `main` branch to GitHub, then trigger a Render redeploy with clear cache.

Latest verification:

- GitHub `origin/main` now points to commit `e886657` and includes Java 17 build settings.
- Confirmed there is no `COPY migration-backup ./migration-backup` line in GitHub Dockerfile.
- Render deployment completed successfully and service is live.
- Live backend URL: `https://prescripto-sem2-backend.onrender.com`
- Main frontend URL: `https://frontend-three-blond-79.vercel.app`
- Admin frontend URL: `https://admin-swart-mu.vercel.app`
- Pharmacist frontend URL: `https://pharmacist-pied.vercel.app`
- CORS verification: backend returns `200` with correct `Access-Control-Allow-Origin` for all three Vercel origins.
- End-to-end API parity smoke run against Render backend: SUCCESS.

Step 3 blocker:

- Main frontend opens on Vercel, but shows "Backend URL is not configured" toast.
- Immediate fix: add `VITE_API_URL` (and `VITE_BACKEND_URL` for compatibility) in Vercel project Environment Variables, then redeploy.

Latest diagnosis:

- Backend is reachable (`/actuator/health` is UP and `/api/doctor/list` responds).
- Browser requests from Vercel were previously blocked by backend CORS (`403 Invalid CORS request`).
- CORS fix verified: preflight and GET now return `200 OK` for origin `https://prescripto-sem2-frontend-lihowswxz7.vercel.app`.
- Main frontend deployment confirmed at `https://prescripto-sem2-frontend-lihowswxz7.vercel.app`.

## Data Safety Routine

Latest local backup created:

- backend/migration-backup/mysql_20260408_152838.sql

Latest data migration (local MySQL -> Railway MySQL):

- Migration status: SUCCESS
- Railway pre-import backup: `backend/migration-backup/railway_before_import_20260408_182610.sql`
- Local export used for import: `backend/migration-backup/local_export_20260408_182610.sql`
- Verified counts after import on Railway:
	- users: 6
	- doctors: 16
	- appointments: 11
	- prescriptions: 1
	- pharmacy_orders: 1
	- pharmacies: 2

Before any major deployment change, run a backup:

```powershell
cd backend
./scripts/backup-mysql.ps1
```

Restore from latest backup if needed:

```powershell
cd backend
./scripts/restore-mysql.ps1
```

Restore from a specific file:

```powershell
cd backend
./scripts/restore-mysql.ps1 -BackupFile "migration-backup/mysql_YYYYMMDD_HHmmss.sql"
```

## Notes

- Local credentials are stored in git-ignored local env files.
- Do not commit secrets to source control.
- Rotate credentials after deployment to production.
