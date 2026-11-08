import { DOCUMENT, isPlatformBrowser } from '@angular/common';
import { inject, Injectable, PLATFORM_ID } from '@angular/core';
import type { TurnstileApi, TurnstileWindow } from './turnstile.types';

@Injectable({
  providedIn: 'root',
})
export class TurnstileScriptService {
  private readonly document = inject(DOCUMENT);
  private readonly platformId = inject(PLATFORM_ID);

  private pending: Promise<TurnstileApi> | null = null;

  load(): Promise<TurnstileApi> {
    if (!isPlatformBrowser(this.platformId)) {
      return Promise.reject(new Error('Turnstile is available only in the browser'));
    }

    if (this.pending) {
      return this.pending;
    }

    const view = this.document.defaultView as TurnstileWindow | null;

    if (!view) {
      return Promise.reject(new Error('Browser window is unavailable'));
    }

    this.pending = this.loadScript(view).catch((error: unknown) => {
      this.pending = null;
      throw error;
    });

    return this.pending;
  }

  private loadScript(view: TurnstileWindow): Promise<TurnstileApi> {
    return new Promise((resolve, reject) => {
      let script: HTMLScriptElement | null = null;

      const cleanup = (): void => {
        view.clearTimeout(timeout);
        script?.removeEventListener('load', ready);
        script?.removeEventListener('error', failed);
      };

      const failed = (): void => {
        cleanup();
        script?.remove();
        reject(new Error('Cannot load Turnstile'));
      };

      const ready = (): void => {
        const api = view.turnstile;

        if (!api) {
          failed();
          return;
        }

        cleanup();
        resolve(api);
      };

      const timeout = view.setTimeout(failed, 15_000);

      if (view.turnstile) {
        ready();
        return;
      }

      script = this.document.createElement('script');
      script.src = 'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit';
      script.async = true;
      script.defer = true;
      script.addEventListener('load', ready);
      script.addEventListener('error', failed);

      this.document.head.appendChild(script);
    });
  }
}
