import type { Provider } from '@angular/core';
import { of } from 'rxjs';
import { TurnstileApiService } from '../core/turnstile/turnstile-api.service';

export function provideDisabledTurnstileTesting(): Provider {
  return {
    provide: TurnstileApiService,
    useValue: {
      getConfiguration: () =>
        of({
          enabled: false,
          siteKey: null,
        }),
    },
  };
}
