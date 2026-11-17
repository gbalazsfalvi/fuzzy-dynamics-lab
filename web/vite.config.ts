import { defineConfig } from "vite";

export default defineConfig({
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      "/api": {
        target: "http://127.0.0.1:4180",
        changeOrigin: true,
        configure(proxy) {
          proxy.on("proxyReq", (proxyRequest, request) => {
            // Rewrite only this local dev server's same-origin requests. Foreign origins
            // and cross-site metadata remain untouched so the backend rejects them.
            if (
              request.headers.origin === "http://127.0.0.1:5173" &&
              (!request.headers["sec-fetch-site"] ||
                request.headers["sec-fetch-site"] === "same-origin")
            ) {
              proxyRequest.setHeader("origin", "http://127.0.0.1:4180");
            }
          });
        },
      },
    },
  },
  preview: { port: 5173, strictPort: true },
});
