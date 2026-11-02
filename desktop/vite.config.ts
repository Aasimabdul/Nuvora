import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  clearScreen: false,
  server: { strictPort: true, port: 1420 },
  envPrefix: ["VITE_", "TAURI_ENV_*"],
  test: { environment: "jsdom", globals: true }
});