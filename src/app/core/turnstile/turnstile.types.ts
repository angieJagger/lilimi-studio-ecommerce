export type TurnstileAction = 'project-inquiry' | 'registration';

export interface TurnstileRenderOptions {
  sitekey: string;
  action: TurnstileAction;
  theme: 'light' | 'dark' | 'auto';
  size: 'normal' | 'compact' | 'flexible';
  language: 'pl' | 'en';
  'response-field': boolean;
  callback: (token: string) => void;
  'expired-callback': () => void;
  'error-callback': (errorCode: string) => boolean | void;
  'timeout-callback': () => void;
  appearance?: 'always' | 'execute' | 'interaction-only';
}

export interface TurnstileApi {
  ready(callback: () => void): void;

  render(container: HTMLElement, options: TurnstileRenderOptions): string | undefined;

  reset(widgetId: string): void;
  remove(widgetId: string): void;
}

export interface TurnstileWindow extends Window {
  turnstile?: TurnstileApi;
}
