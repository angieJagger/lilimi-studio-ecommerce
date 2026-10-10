import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject, Subject } from 'rxjs';
import { vi } from 'vitest';
import { getTranslocoTestingModule } from '../../../testing/transloco-testing';
import { AdminProductApiService } from './admin-product-api.service';
import { AdminProductEdit } from './admin-product-edit';
import type { AdminProductDetails } from './admin-product.model';

describe('AdminProductEdit', () => {
  let fixture: ComponentFixture<AdminProductEdit>;
  let element: HTMLElement;
  let result: Subject<AdminProductDetails>;
  let saveResult: Subject<AdminProductDetails>;
  let params: BehaviorSubject<ReturnType<typeof convertToParamMap>>;

  const api = {
    getProduct: vi.fn(),
    updateTranslations: vi.fn(),
  };

  const product: AdminProductDetails = {
    id: 'pattern-001',
    version: 0,
    slug: 'forest-dragon',
    productType: 'digital',
    active: true,
    madeToOrder: false,
    personalizationAvailable: false,
    translations: [
      {
        language: 'en',
        name: 'Forest dragon',
        description: 'A digital embroidery pattern.',
      },
      {
        language: 'pl',
        name: 'Leśny smok',
        description: 'Cyfrowy wzór haftu.',
      },
    ],
  };

  beforeEach(async () => {
    result = new Subject<AdminProductDetails>();
    saveResult = new Subject<AdminProductDetails>();
    params = new BehaviorSubject(convertToParamMap({ id: product.id }));

    api.getProduct.mockReset();
    api.updateTranslations.mockReset();
    api.getProduct.mockReturnValue(result.asObservable());
    api.updateTranslations.mockReturnValue(saveResult.asObservable());

    await TestBed.configureTestingModule({
      imports: [AdminProductEdit, getTranslocoTestingModule()],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { paramMap: params.asObservable() },
        },
        { provide: AdminProductApiService, useValue: api },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AdminProductEdit);
    element = fixture.nativeElement;

    await fixture.whenStable();
  });

  async function respond(details: AdminProductDetails): Promise<void> {
    result.next(details);
    result.complete();

    await fixture.whenStable();
  }

  async function fillField(id: string, value: string): Promise<void> {
    const field = element.querySelector<HTMLInputElement | HTMLTextAreaElement>(`#${id}`)!;

    field.value = value;
    field.dispatchEvent(new Event('input', { bubbles: true }));

    await fixture.whenStable();
  }

  async function submitForm(): Promise<void> {
    element
      .querySelector('form')!
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));

    await fixture.whenStable();
  }

  it('should request the product from the route and show loading', () => {
    expect(api.getProduct).toHaveBeenCalledExactlyOnceWith(product.id);
    expect(element.querySelector('output')).not.toBeNull();
    expect(element.querySelector('form')).toBeNull();
  });

  it('should populate both languages and provide a link back', async () => {
    await respond(product);

    expect(element.querySelector<HTMLInputElement>('#product-name-pl')!.value).toBe('Leśny smok');

    expect(element.querySelector<HTMLInputElement>('#product-name-en')!.value).toBe(
      'Forest dragon',
    );

    expect(element.querySelector<HTMLTextAreaElement>('#product-description-pl')!.value).toBe(
      'Cyfrowy wzór haftu.',
    );

    expect(element.querySelector<HTMLTextAreaElement>('#product-description-en')!.value).toBe(
      'A digital embroidery pattern.',
    );

    expect(element.querySelector('a[href="/pl/admin/products"]')).not.toBeNull();

    expect(element.querySelector('[role="alert"]')).toBeNull();
    expect(api.updateTranslations).not.toHaveBeenCalled();
  });

  it('should block editing when a translation is missing', async () => {
    await respond({
      ...product,
      translations: product.translations.filter((translation) => translation.language === 'pl'),
    });

    expect(element.querySelector('[role="alert"]')?.textContent).toContain(
      'Brakuje tłumaczenia PL lub EN.',
    );
    expect(element.querySelector('form')).toBeNull();
    expect(api.updateTranslations).not.toHaveBeenCalled();
  });

  it('should show a missing product message', async () => {
    result.error(new HttpErrorResponse({ status: 404 }));

    await fixture.whenStable();

    expect(element.querySelector('[role="alert"]')?.textContent).toContain(
      'Nie znaleziono produktu.',
    );
    expect(element.querySelector('form')).toBeNull();
  });

  it.each([401, 403])('should show a login link for HTTP %s', async (status) => {
    result.error(new HttpErrorResponse({ status }));

    await fixture.whenStable();

    expect(element.querySelector('[role="alert"]')).not.toBeNull();
    expect(element.querySelector('a[href="/pl/admin/login"]')).not.toBeNull();
    expect(element.querySelector('form')).toBeNull();
  });

    it('should save both languages with the current version and trimmed values', async () => {
      await respond(product);

      await fillField('product-name-pl', '  Nowa nazwa  ');
      await fillField('product-description-pl', '  Nowy opis.  ');
      await fillField('product-name-en', '  Updated name  ');
      await fillField('product-description-en', '  Updated description.  ');

      await submitForm();

      expect(api.updateTranslations).toHaveBeenCalledExactlyOnceWith(product.id, {
        pl: {
          name: 'Nowa nazwa',
          description: 'Nowy opis.',
        },
        en: {
          name: 'Updated name',
          description: 'Updated description.',
        },
        expectedVersion: product.version,
      });

      saveResult.next({
        ...product,
        version: product.version + 1,
        translations: [
          {
            language: 'pl',
            name: 'Nowa nazwa',
            description: 'Nowy opis.',
          },
          {
            language: 'en',
            name: 'Updated name',
            description: 'Updated description.',
          },
        ],
      });
      saveResult.complete();

      await fixture.whenStable();

      expect(element.querySelector('.product-edit__success')?.textContent).toContain(
        'Tłumaczenia zostały zapisane.',
      );

      expect(element.querySelector<HTMLInputElement>('#product-name-pl')!.value).toBe('Nowa nazwa');

      expect(element.querySelector('[role="alert"]')).toBeNull();
    });

    it.each([
      ['product-name-pl', ''],
      ['product-name-en', '   '],
      ['product-description-pl', ''],
      ['product-description-en', '   '],
    ])('should reject an empty or blank field: %s', async (id, value) => {
      await respond(product);
      await fillField(id, value);
      await submitForm();

      expect(api.updateTranslations).not.toHaveBeenCalled();
      expect(element.querySelector('.form-field__errors')).not.toBeNull();
      expect(document.activeElement?.id).toBe(id);
    });

    it.each([
      ['product-name-pl', 201],
      ['product-name-en', 201],
      ['product-description-pl', 5001],
      ['product-description-en', 5001],
    ])('should reject content exceeding the limit: %s', async (id, length) => {
      await respond(product);
      await fillField(id, 'a'.repeat(length));
      await submitForm();

      expect(api.updateTranslations).not.toHaveBeenCalled();
      expect(element.querySelector('.form-field__errors')).not.toBeNull();
    });

    it('should prevent duplicate submissions while saving', async () => {
      await respond(product);

      await submitForm();
      await submitForm();

      expect(api.updateTranslations).toHaveBeenCalledTimes(1);

      expect(element.querySelector<HTMLFieldSetElement>('.product-edit__fields')!.disabled).toBe(
        true,
      );

      saveResult.next({
        ...product,
        version: product.version + 1,
      });
      saveResult.complete();

      await fixture.whenStable();

      expect(element.querySelector<HTMLFieldSetElement>('.product-edit__fields')!.disabled).toBe(
        false,
      );
    });
      it.each([
        [409, 'Produkt został zmieniony.'],
        [0, 'Nie udało się potwierdzić zapisu tłumaczeń.'],
      ])(
        'should preserve entered content and require reload after HTTP %s',
        async (status, message) => {
          await respond(product);

          await fillField('product-name-pl', 'Moja niezapisana nazwa');
          await submitForm();

          saveResult.error(
            new HttpErrorResponse({
              status,
              error: status === 409 ? { code: 'PRODUCT_VERSION_CONFLICT' } : null,
            }),
          );

          await fixture.whenStable();

          expect(element.querySelector('[role="alert"]')?.textContent).toContain(message);

          expect(element.querySelector<HTMLInputElement>('#product-name-pl')!.value).toBe(
            'Moja niezapisana nazwa',
          );

          expect(
            element.querySelector<HTMLFieldSetElement>('.product-edit__fields')!.disabled,
          ).toBe(true);

          await submitForm();

          expect(api.updateTranslations).toHaveBeenCalledTimes(1);

          result = new Subject<AdminProductDetails>();
          api.getProduct.mockReturnValue(result.asObservable());

          element.querySelector<HTMLButtonElement>('[data-testid="reload-product"]')!.click();

          await fixture.whenStable();

          expect(api.getProduct).toHaveBeenCalledTimes(2);

          await respond({
            ...product,
            version: product.version + 1,
            translations: product.translations.map((translation) =>
              translation.language === 'pl'
                ? { ...translation, name: 'Nazwa zapisana na serwerze' }
                : translation,
            ),
          });

          expect(element.querySelector('[role="alert"]')).toBeNull();

          expect(element.querySelector<HTMLInputElement>('#product-name-pl')!.value).toBe(
            'Nazwa zapisana na serwerze',
          );

          expect(
            element.querySelector<HTMLFieldSetElement>('.product-edit__fields')!.disabled,
          ).toBe(false);
        },
      );

      it('should use the returned version for the next save', async () => {
        await respond(product);
        await submitForm();

        saveResult.next({
          ...product,
          version: product.version + 1,
        });
        saveResult.complete();

        await fixture.whenStable();

        saveResult = new Subject<AdminProductDetails>();
        api.updateTranslations.mockReturnValue(saveResult.asObservable());

        await fillField('product-name-pl', 'Kolejna nazwa');
        await submitForm();

        expect(api.updateTranslations).toHaveBeenLastCalledWith(
          product.id,
          expect.objectContaining({
            expectedVersion: product.version + 1,
            pl: {
              name: 'Kolejna nazwa',
              description: 'Cyfrowy wzór haftu.',
            },
          }),
        );

        saveResult.next({
          ...product,
          version: product.version + 2,
          translations: product.translations.map((translation) =>
            translation.language === 'pl' ? { ...translation, name: 'Kolejna nazwa' } : translation,
          ),
        });
        saveResult.complete();

        await fixture.whenStable();
      });
});
