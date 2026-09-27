import { mergeApplicationConfig, ApplicationConfig } from '@angular/core';
import { provideServerRendering, withRoutes } from '@angular/ssr';
import { appConfig } from './app.config';
import { serverRoutes } from './app.routes.server';
import { API_BASE_URL } from './core/api/api-base-url';

const serverConfig: ApplicationConfig = {
  providers: [
    provideServerRendering(withRoutes(serverRoutes)),
    {
      provide: API_BASE_URL,
      useFactory: () => {
        const url = process.env['BACKEND_API_URL'] ?? 'http://127.0.0.1:8080/api';

        let end = url.length;

        while (end > 0 && url[end - 1] === '/') {
          end--;
        }

        return url.slice(0, end);
      },
    },
  ],
};

export const config = mergeApplicationConfig(appConfig, serverConfig);
