import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { resolve } from "node:path";

export default defineConfig(({ mode }) => {
  const springBuild = mode === "spring";

  return {
    plugins: [react()],
    server: {
      port: 5173,
      proxy: {
        "/api": "http://localhost:8080",
        "/actuator": "http://localhost:8080",
      },
    },
    build: {
      outDir: springBuild
        ? resolve(__dirname, "../fes/src/main/resources/static")
        : resolve(__dirname, "dist"),
      emptyOutDir: true,
      assetsDir: ".",
      rollupOptions: {
        output: {
          entryFileNames: springBuild ? "app.js" : "app-[hash].js",
          chunkFileNames: springBuild ? "chunks/[name].js" : "chunks/[name]-[hash].js",
          assetFileNames: (assetInfo) => {
            if (assetInfo.name?.endsWith(".css")) {
              return springBuild ? "app.css" : "app-[hash][extname]";
            }
            return springBuild ? "assets/[name][extname]" : "assets/[name]-[hash][extname]";
          },
        },
      },
    },
  };
});
