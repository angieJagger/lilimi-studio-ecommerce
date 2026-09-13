import { DOCUMENT, inject } from '@angular/core';
import { ResolveFn } from '@angular/router';
import { Translation, TranslocoService } from '@jsverse/transloco';
import { tap } from 'rxjs';

export const languageResolver: ResolveFn<Translation> = (route) => {
  const transloco = inject(TranslocoService);
  const document = inject(DOCUMENT);
  const language = route.data['language'];

  if (language !== 'pl' && language !== 'en') {
    throw new Error(`Unsupported route language: ${language}`);
  }

  return transloco.load(language).pipe(
    tap(() => {
      transloco.setActiveLang(language);
      document.documentElement.lang = language;
    }),
  );
};
