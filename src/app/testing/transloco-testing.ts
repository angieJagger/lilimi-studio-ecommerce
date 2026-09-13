import { TranslocoTestingModule } from '@jsverse/transloco';
import pl from '../core/i18n/pl.json';
import en from '../core/i18n/en.json';

export function getTranslocoTestingModule() {
  return TranslocoTestingModule.forRoot({
    langs: { pl, en },
    translocoConfig: {
      availableLangs: ['pl', 'en'],
      defaultLang: 'pl',
      reRenderOnLangChange: true,
    },
    preloadLangs: true,
  });
}
