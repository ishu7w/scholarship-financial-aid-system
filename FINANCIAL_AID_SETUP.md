# Java setup

The Java backend now handles the whole project's application logic, including the existing financial-aid implementation.

Install JDK 17+, Maven and Node.js 22, then run:

```bash
npm ci
npm run dev
```

Open http://localhost:3000/financial-aid. The existing need assessment, three aid programs, applications and review/disbursement lifecycle are retained.

See [README.md](README.md) for the module structure, [OOP_GUIDE.md](OOP_GUIDE.md) for OOP examples, and [DEPLOYMENT.md](DEPLOYMENT.md) for local storage and Vercel settings.
