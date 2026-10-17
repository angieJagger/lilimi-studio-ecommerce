import { DOCUMENT } from '@angular/common';
import { inject, Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root',
})
export class PaymentRedirectService {
  private readonly document = inject(DOCUMENT);

  redirect(value: string): void {
    const url = new URL(value);

    if (
      url.protocol !== 'https:' ||
      url.hostname !== 'checkout.stripe.com' ||
      url.username !== '' ||
      url.password !== '' ||
      (url.port !== '' && url.port !== '443')
    ) {
      throw new Error('Invalid Stripe Checkout URL');
    }

    const view = this.document.defaultView;

    if (!view) {
      throw new Error('Browser navigation is unavailable');
    }

    view.location.assign(url.href);
  }
}
