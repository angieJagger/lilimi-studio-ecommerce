import {
  afterNextRender,
  Component,
  DestroyRef,
  effect,
  ElementRef,
  inject,
  input,
  output,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslocoPipe } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { TurnstileApiService } from './turnstile-api.service';
import { TurnstileScriptService } from './turnstile-script.service';
import type { TurnstileAction, TurnstileApi } from './turnstile.types';

export interface TurnstileState {
  ready: boolean;
  token: string | null;
}

@Component({
  selector: 'app-turnstile-widget',
  imports: [TranslocoPipe],
  templateUrl: './turnstile-widget.html',
  styleUrl: './turnstile-widget.scss',
})
export class TurnstileWidget {
  private readonly configuration = inject(TurnstileApiService);
  private readonly script = inject(TurnstileScriptService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly container = viewChild.required<ElementRef<HTMLElement>>('widget');

  readonly action = input.required<TurnstileAction>();
  readonly language = input<'pl' | 'en'>('pl');
  readonly stateChange = output<TurnstileState>();

  protected readonly status = signal<'loading' | 'disabled' | 'pending' | 'verified' | 'error'>(
    'loading',
  );

  private api: TurnstileApi | null = null;
  private siteKey: string | null = null;
  private widgetId: string | null = null;
  private initialization = 0;
  private widgetGeneration = 0;

  constructor() {
    afterNextRender(() => void this.initialize());

    effect(() => {
      const action = this.action();
      const language = this.language();

      if (this.api && this.siteKey) {
        untracked(() => this.renderWidget(action, language));
      }
    });

    this.destroyRef.onDestroy(() => {
      this.initialization++;
      this.removeWidget();
    });
  }

  reset(): void {
    if (this.status() === 'disabled') {
      return;
    }

    this.stateChange.emit({ ready: false, token: null });

    if (!this.api || this.widgetId === null) {
      this.status.set('error');
      return;
    }

    try {
      this.status.set('pending');
      this.api.reset(this.widgetId);
    } catch {
      this.fail();
    }
  }

  protected retry(): void {
    void this.initialize();
  }

  private async initialize(): Promise<void> {
    const attempt = ++this.initialization;

    this.removeWidget();
    this.siteKey = null;
    this.status.set('loading');
    this.stateChange.emit({ ready: false, token: null });

    try {
      const configuration = await firstValueFrom(
        this.configuration.getConfiguration().pipe(takeUntilDestroyed(this.destroyRef)),
      );

      if (this.destroyRef.destroyed || attempt !== this.initialization) {
        return;
      }

      if (!configuration.enabled) {
        this.status.set('disabled');
        this.stateChange.emit({ ready: true, token: null });
        return;
      }

      if (!configuration.siteKey) {
        throw new Error('Missing Turnstile site key');
      }

      const api = await this.script.load();

      if (this.destroyRef.destroyed || attempt !== this.initialization) {
        return;
      }

      this.api = api;
      this.siteKey = configuration.siteKey;
      this.renderWidget(this.action(), this.language());
    } catch {
      if (!this.destroyRef.destroyed && attempt === this.initialization) {
        this.fail();
      }
    }
  }

  private renderWidget(action: TurnstileAction, language: 'pl' | 'en'): void {
    this.removeWidget();

    if (!this.api || !this.siteKey) {
      return;
    }

    const generation = this.widgetGeneration;
    const isCurrent = (): boolean =>
      !this.destroyRef.destroyed && generation === this.widgetGeneration;

    this.status.set('pending');
    this.stateChange.emit({ ready: false, token: null });

    try {
      const widgetId = this.api.render(this.container().nativeElement, {
        sitekey: this.siteKey,
        action,
        language,
        theme: 'light',
        appearance: 'interaction-only',
        size: 'flexible',
        'response-field': false,
        callback: (token) => {
          if (!isCurrent()) {
            return;
          }

          if (!token) {
            this.fail();
            return;
          }

          this.status.set('verified');
          this.stateChange.emit({ ready: true, token });
        },
        'expired-callback': () => {
          if (isCurrent()) {
            this.status.set('pending');
            this.stateChange.emit({ ready: false, token: null });
          }
        },
        'error-callback': () => {
          if (isCurrent()) {
            this.fail();
          }

          return true;
        },
        'timeout-callback': () => {
          if (isCurrent()) {
            this.fail();
          }
        },
      });

      if (widgetId === undefined) {
        this.fail();
        return;
      }

      this.widgetId = widgetId;
    } catch {
      this.fail();
    }
  }

  private removeWidget(): void {
    this.widgetGeneration++;

    if (this.api && this.widgetId !== null) {
      this.api.remove(this.widgetId);
    }

    this.widgetId = null;
  }

  private fail(): void {
    this.status.set('error');
    this.stateChange.emit({ ready: false, token: null });
  }
}
