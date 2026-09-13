import { Injectable } from '@angular/core';
import { Translation, TranslocoLoader } from '@jsverse/transloco';
import { Observable, of, throwError } from 'rxjs';

import pl from './pl.json';
import en from './en.json';

@Injectable({ providedIn: 'root' })
export class TranslationLoader implements TranslocoLoader {
  getTranslation(lang: string): Observable<Translation> {
    switch (lang) {
      case 'pl':
        return of(pl);
      case 'en':
        return of(en);
      default:
        return throwError(() => new Error(`Unsupported language: ${lang}`));
    }
  }
}
