# Vericov Shadcn Admin Template

Standalone Next.js + shadcn/ui admin dashboard template for new Vericov webapps.

## Commands

```bash
npm install
npm run dev
npm run build
npm run lint
npm run test
npm run test -- --coverage
npm run test:e2e
```

The dashboard lives at `/dashboard/default`; `/` redirects there.

## Structure

- `app/` contains the App Router entrypoints.
- `components/app-shell/` contains the reusable sidebar, top bar, command palette, theme controls, and profile menus.
- `components/dashboard/` contains the first Classic Dashboard page and widgets.
- `lib/` contains typed local data, navigation, validation, and immutable state helpers.

This template intentionally uses local synthetic data only. It is ready to be wired to product APIs in individual apps.
