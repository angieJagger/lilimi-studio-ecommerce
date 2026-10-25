import { InjectionToken } from '@angular/core';

// Browser-facing URL: never substitute the backend's internal SSR origin here.
export const PUBLIC_MEDIA_BASE_URL = new InjectionToken<string>('PUBLIC_MEDIA_BASE_URL', {
  providedIn: 'root',
  factory: () => '/api',
});
