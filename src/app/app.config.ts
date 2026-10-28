import { DOCUMENT, ViewportScroller } from '@angular/common';
import { ApplicationConfig, provideBrowserGlobalErrorListeners, isDevMode, inject, provideAppInitializer } from '@angular/core';
import { provideRouter, withInMemoryScrolling } from '@angular/router';
import { routes } from './app.routes';
import { provideClientHydration } from '@angular/platform-browser';
import { provideTransloco } from '@jsverse/transloco';
import { TranslationLoader } from './core/i18n/translation-loader';
import { provideHttpClient } from '@angular/common/http';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideAppInitializer(() => {
      const viewport = inject(ViewportScroller);
      const document = inject(DOCUMENT);
      viewport.setOffset(() => [
        0,
        (document.querySelector('app-header')?.getBoundingClientRect().height ?? 80) + 16,
      ]);
    }),
    provideHttpClient(),
    provideRouter(
      routes,
      withInMemoryScrolling({
        anchorScrolling: 'enabled',
        scrollPositionRestoration: 'enabled',
      }),
    ),
    provideClientHydration(),
    provideTransloco({
      config: {
        availableLangs: ['pl', 'en'],
        defaultLang: 'pl',
        reRenderOnLangChange: true,
        prodMode: !isDevMode(),
      },
      loader: TranslationLoader,
    }),
  ],
};
