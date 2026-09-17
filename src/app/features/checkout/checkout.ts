import { Component, computed, inject, linkedSignal, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { email, form, FormField, required, validate } from '@angular/forms/signals';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { CartService } from '../cart/cart.service';
import { deliveryMethods, DeliveryMethodId } from './delivery.model';
import type { CreateOrderRequest, OrderDelivery, OrderItem } from './order.model';

@Component({
  selector: 'app-checkout',
  imports: [RouterLink, TranslocoPipe, FormField],
  templateUrl: './checkout.html',
  styleUrl: './checkout.scss',
})
export class Checkout {
  protected readonly cart = inject(CartService);

  protected readonly requiresShipping = computed(() => this.cart.sweatshirts().length > 0);

  protected readonly deliveryMethods = deliveryMethods;

  protected readonly selectedDeliveryId = linkedSignal({
    source: this.requiresShipping,
    computation: (): DeliveryMethodId | '' => '',
  });

  protected readonly requiresAddress = computed(
    () => this.requiresShipping() && this.selectedDelivery()?.kind === 'courier',
  );

  protected readonly addressModel = signal({
    addressLine1: '',
    addressLine2: '',
    postalCode: '',
    city: '',
  });

  protected readonly addressForm = form(this.addressModel, (path) => {
    for (const field of ['addressLine1', 'postalCode', 'city'] as const) {
      required(path[field], {
        when: () => this.requiresAddress(),
        message: 'checkout.errors.addressRequired',
      });

      validate(path[field], ({ value }) => {
        const text = value();

        return this.requiresAddress() && text.length > 0 && text.trim() === ''
          ? {
              kind: 'blank',
              message: 'checkout.errors.addressRequired',
            }
          : undefined;
      });
    }

    validate(path.postalCode, ({ value }) => {
      const code = value().trim();

      if (!this.requiresAddress() || code === '') {
        return undefined;
      }

      return /^\d{2}-\d{3}$/.test(code)
        ? undefined
        : {
            kind: 'postalCode',
            message: 'checkout.errors.postalCodeInvalid',
          };
    });
  });

  protected readonly addressFields = [
    { name: 'addressLine1', autocomplete: 'address-line1' },
    { name: 'addressLine2', autocomplete: 'address-line2' },
    { name: 'postalCode', autocomplete: 'postal-code' },
    { name: 'city', autocomplete: 'address-level2' },
  ] as const;

  protected readonly selectedDelivery = computed(() =>
    this.deliveryMethods.find((method) => method.id === this.selectedDeliveryId()),
  );

  protected selectDelivery(id: DeliveryMethodId): void {
    if (this.requiresShipping()) {
      this.selectedDeliveryId.set(id);
    }
  }

  protected readonly deliveryPriceInGrosz = computed<number | null>(() => {
    if (!this.requiresShipping()) {
      return 0;
    }

    return this.selectedDelivery()?.priceInGrosz ?? null;
  });

  protected readonly totalInGrosz = computed<number | null>(() => {
    const deliveryPrice = this.deliveryPriceInGrosz();

    if (deliveryPrice === null) {
      return null;
    }

    return this.cart.subtotalInGrosz() + deliveryPrice;
  });

  private readonly transloco = inject(TranslocoService);

  private readonly activeLanguage = toSignal(this.transloco.langChanges$, {
    initialValue: this.transloco.getActiveLang(),
  });

  protected readonly language = computed<'pl' | 'en'>(() =>
    this.activeLanguage() === 'en' ? 'en' : 'pl',
  );

  protected readonly priceFormatter = computed(
    () =>
      new Intl.NumberFormat(this.language(), {
        style: 'currency',
        currency: 'PLN',
      }),
  );

  protected readonly formattedSubtotal = computed(() =>
    this.priceFormatter().format(this.cart.subtotalInGrosz() / 100),
  );

  protected readonly formattedDeliveryPrice = computed(() => {
    const price = this.deliveryPriceInGrosz();

    return price === null ? null : this.priceFormatter().format(price / 100);
  });

  protected readonly formattedTotal = computed(() => {
    const total = this.totalInGrosz();

    return total === null ? null : this.priceFormatter().format(total / 100);
  });

  protected readonly contactModel = signal({
    fullName: '',
    email: '',
    phone: '',
  });

  protected readonly contactForm = form(this.contactModel, (path) => {
    required(path.fullName, {
      message: 'checkout.errors.nameRequired',
    });

    validate(path.fullName, ({ value }) => {
      const name = value();

      return name.length > 0 && name.trim() === ''
        ? {
            kind: 'blank',
            message: 'checkout.errors.nameRequired',
          }
        : undefined;
    });

    required(path.email, {
      message: 'checkout.errors.emailRequired',
    });

    email(path.email, {
      message: 'checkout.errors.emailInvalid',
    });
  });

  protected readonly contactFields = [
    { name: 'fullName', type: 'text', autocomplete: 'name' },
    { name: 'email', type: 'email', autocomplete: 'email' },
    { name: 'phone', type: 'tel', autocomplete: 'tel' },
  ] as const;

  protected readonly submissionAttempted = signal(false);
  protected readonly checkoutStep = signal<'details' | 'review'>('details');

  protected readonly deliveryMissing = computed(
    () => this.requiresShipping() && !this.selectedDelivery(),
  );

  protected readonly canContinue = computed(
    () =>
      this.cart.isReady() &&
      this.cart.itemCount() > 0 &&
      this.contactForm().valid() &&
      (!this.requiresAddress() || this.addressForm().valid()) &&
      !this.deliveryMissing() &&
      this.totalInGrosz() !== null,
  );

  protected readonly orderRequest = computed<CreateOrderRequest | null>(() => {
    if (!this.canContinue()) {
      return null;
    }

    const contact = this.contactModel();
    const phone = contact.phone.trim();

    let delivery: OrderDelivery;

    if (this.requiresShipping()) {
      const method = this.selectedDelivery();

      if (!method || method.kind !== 'courier' || method.id === 'inpost-locker') {
        return null;
      }

      const address = this.addressModel();
      const addressLine2 = address.addressLine2.trim();

      delivery = {
        kind: 'courier',
        methodId: method.id,
        address: {
          addressLine1: address.addressLine1.trim(),
          ...(addressLine2 ? { addressLine2 } : {}),
          postalCode: address.postalCode.trim(),
          city: address.city.trim(),
          countryCode: 'PL',
        },
      };
    } else {
      delivery = {
        kind: 'digital',
      };
    }

    const items: OrderItem[] = [];

    for (const product of this.cart.patterns()) {
      items.push({
        kind: 'digital',
        productId: product.id,
        quantity: 1,
      });
    }

    for (const item of this.cart.sweatshirts()) {
      items.push({
        kind: 'sweatshirt',
        productId: item.productId,
        patternId: item.patternId,
        configuration: {
          fit: item.configuration.fit,
          size: item.configuration.size,
          color: item.configuration.color,
          embroideryOptionId: item.configuration.embroideryOptionId,
        },
        quantity: item.quantity,
      });
    }

    return {
      language: this.language(),
      contact: {
        fullName: contact.fullName.trim(),
        email: contact.email.trim(),
        ...(phone ? { phone } : {}),
      },
      delivery,
      items,
    };
  });

  protected continueToReview(event: Event): void {
    event.preventDefault();
    this.submissionAttempted.set(true);

    for (const field of this.contactFields) {
      this.contactForm[field.name]().markAsTouched();
    }

    if (this.requiresAddress()) {
      for (const field of this.addressFields) {
        this.addressForm[field.name]().markAsTouched();
      }
    }

    if (this.orderRequest() === null) {
      return;
    }

    this.checkoutStep.set('review');
  }

  protected returnToDetails(): void {
    this.checkoutStep.set('details');
  }
}
