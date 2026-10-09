import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProjectInquiry } from './project-inquiry';
import { getTranslocoTestingModule } from '../../testing/transloco-testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject } from 'rxjs';
import { vi } from 'vitest';
import { ProjectInquiryApiService } from './project-inquiry-api.service';
import type { CreateInquiryResponse } from './project-inquiry.model';
import {
  createProductCatalogMock,
  provideProductCatalogTesting,
} from '../../testing/product-catalog-testing';
import { ProductCatalogService } from '../products/product-catalog.service';

describe('ProjectInquiry', () => {
  let component: ProjectInquiry;
  let fixture: ComponentFixture<ProjectInquiry>;
  let inquiryResult: Subject<CreateInquiryResponse>;

  const api = {
    createInquiry: vi.fn(),
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProjectInquiry, getTranslocoTestingModule()],
      providers: [
        provideRouter([]),
        { provide: ProjectInquiryApiService, useValue: api },
        provideProductCatalogTesting(),
      ],
    }).compileComponents();

    inquiryResult = new Subject<CreateInquiryResponse>();

    api.createInquiry.mockReset();
    api.createInquiry.mockReturnValue(inquiryResult.asObservable());

    fixture = TestBed.createComponent(ProjectInquiry);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  async function fillRequiredFields(): Promise<void> {
    const element = fixture.nativeElement as HTMLElement;

    const values = {
      'inquiry-name': 'Anna',
      'inquiry-email': 'anna@example.com',
      'inquiry-project-type': 'website',
      'inquiry-description': 'Potrzebuję strony dla pracowni haftu.',
    };

    for (const [id, value] of Object.entries(values)) {
      const control = element.querySelector<
        HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement
      >(`#${id}`)!;

      control.value = value;
      control.dispatchEvent(new Event('input', { bubbles: true }));

      if (control.tagName === 'SELECT') {
        control.dispatchEvent(new Event('change', { bubbles: true }));
      }
    }

    await fixture.whenStable();
  }

  async function fillField(id: string, value: string): Promise<void> {
    const element = fixture.nativeElement as HTMLElement;
    const control = element.querySelector<HTMLInputElement | HTMLTextAreaElement>(`#${id}`)!;

    control.value = value;
    control.dispatchEvent(new Event('input', { bubbles: true }));

    await fixture.whenStable();
  }

  async function submitForm(): Promise<void> {
    const element = fixture.nativeElement as HTMLElement;

    element.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();

    await fixture.whenStable();
  }

  it('should show server confirmation and hide the form after submission', async () => {
    await fillRequiredFields();

    const element = fixture.nativeElement as HTMLElement;

    element.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();

    await fixture.whenStable();

    inquiryResult.next({
      id: '1548c928-f278-4cbb-b8d0-d57455c72f91',
      createdAt: '2026-10-09T08:00:00Z',
      status: 'new',
    });
    inquiryResult.complete();

    await fixture.whenStable();

    expect(element.querySelector('form')).toBeNull();
    expect(element.querySelector('output')?.textContent).toContain('Zapytanie zostało zapisane.');
    expect(element.textContent).toContain('1548c928-f278-4cbb-b8d0-d57455c72f91');
  });

  it('should prevent duplicate submission while the request is pending', async () => {
    await fillRequiredFields();

    const element = fixture.nativeElement as HTMLElement;
    const form = element.querySelector<HTMLFormElement>('form')!;

    for (let attempt = 0; attempt < 2; attempt++) {
      form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    }

    await fixture.whenStable();

    expect(api.createInquiry).toHaveBeenCalledOnce();
    expect(element.querySelector<HTMLFieldSetElement>('fieldset')!.disabled).toBe(true);
  });

  it('should preserve entered data and show an error when submission fails', async () => {
    await fillRequiredFields();

    const element = fixture.nativeElement as HTMLElement;

    element.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();

    await fixture.whenStable();

    inquiryResult.error(
      new HttpErrorResponse({
        status: 0,
        statusText: 'Unknown Error',
      }),
    );

    await fixture.whenStable();

    expect(element.querySelector('[role="alert"]')?.textContent).toContain(
      'Nie udało się potwierdzić wysłania zapytania.',
    );
    expect(element.querySelector<HTMLInputElement>('#inquiry-name')!.value).toBe('Anna');
    expect(element.querySelector<HTMLTextAreaElement>('#inquiry-description')!.value).toBe(
      'Potrzebuję strony dla pracowni haftu.',
    );
    expect(element.querySelector<HTMLFieldSetElement>('fieldset')!.disabled).toBe(false);
    expect(element.querySelector('.project-inquiry__confirmation')).toBeNull();
  });

  it('should show required errors and focus the first invalid field on submit', async () => {
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('.form-field__errors')).toBeNull();

    element.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
    await fixture.whenStable();

    const invalidFields = element.querySelectorAll('[aria-invalid="true"]');

    expect(invalidFields.length).toBe(4);
    expect(document.activeElement).toBe(element.querySelector('#inquiry-name'));
  });

  it('should submit valid required fields without an inspiration URL', async () => {
    await fillRequiredFields();

    const element = fixture.nativeElement as HTMLElement;

    element.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();

    await fixture.whenStable();

    expect(element.querySelector('[aria-invalid="true"]')).toBeNull();

    expect(api.createInquiry).toHaveBeenCalledExactlyOnceWith({
      language: 'pl',
      name: 'Anna',
      email: 'anna@example.com',
      projectType: 'website',
      description: 'Potrzebuję strony dla pracowni haftu.',
      inspirationUrl: null,
      productId: null,
    });
  });

  it('should reject an invalid inspiration URL', async () => {
    await fillRequiredFields();

    const element = fixture.nativeElement as HTMLElement;
    const inspiration = element.querySelector<HTMLInputElement>('#inquiry-inspiration')!;

    inspiration.value = 'abc';
    inspiration.dispatchEvent(new Event('input', { bubbles: true }));
    await fixture.whenStable();

    element.querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
    await fixture.whenStable();

    expect(inspiration.getAttribute('aria-invalid')).toBe('true');
    expect(element.querySelector('#inquiry-inspiration-errors')).not.toBeNull();
    expect(api.createInquiry).not.toHaveBeenCalled();
  });

  it('should reject names and descriptions containing only spaces', async () => {
    for (const id of ['inquiry-name', 'inquiry-description']) {
      await fillRequiredFields();
      await fillField(id, '   ');
      await submitForm();

      const element = fixture.nativeElement as HTMLElement;

      expect(element.querySelector(`#${id}`)?.getAttribute('aria-invalid')).toBe('true');

      expect(api.createInquiry).not.toHaveBeenCalled();
    }
  });

  it('should reject values exceeding backend length limits', async () => {
    const cases = [
      {
        id: 'inquiry-name',
        value: 'a'.repeat(151),
        message: 'Imię może mieć maksymalnie 150 znaków.',
      },
      {
        id: 'inquiry-email',
        value: `${'a'.repeat(243)}@example.com`,
        message: 'Adres e-mail może mieć maksymalnie 254 znaki.',
      },
      {
        id: 'inquiry-description',
        value: 'a'.repeat(5001),
        message: 'Opis może mieć maksymalnie 5000 znaków.',
      },
      {
        id: 'inquiry-inspiration',
        value: `https://example.com/${'a'.repeat(2048)}`,
        message: 'Link może mieć maksymalnie 2048 znaków.',
      },
    ];

    for (const testCase of cases) {
      await fillRequiredFields();
      await fillField('inquiry-inspiration', '');
      await fillField(testCase.id, testCase.value);
      await submitForm();

      const element = fixture.nativeElement as HTMLElement;

      expect(element.textContent).toContain(testCase.message);
      expect(api.createInquiry).not.toHaveBeenCalled();
    }
  });

  it('should reject inspiration links with credentials or spaces', async () => {
    const invalidUrls = [
      'https://user:password@example.com',
      'https://example.com/path with spaces',
    ];

    for (const url of invalidUrls) {
      await fillRequiredFields();
      await fillField('inquiry-inspiration', url);
      await submitForm();

      const element = fixture.nativeElement as HTMLElement;

      expect(element.querySelector('#inquiry-inspiration-errors')).not.toBeNull();

      expect(api.createInquiry).not.toHaveBeenCalled();
    }
  });
});


describe('ProjectInquiry product context', () => {
  let harness: RouterTestingHarness;
  let catalog: ReturnType<typeof createProductCatalogMock>;
  let inquiryResult: Subject<CreateInquiryResponse>;

  const api = {
    createInquiry: vi.fn(),
  };

  beforeEach(async () => {
    catalog = createProductCatalogMock();
    inquiryResult = new Subject<CreateInquiryResponse>();
    
    api.createInquiry.mockReset();
    api.createInquiry.mockReturnValue(inquiryResult.asObservable());

    await TestBed.configureTestingModule({
      imports: [ProjectInquiry, getTranslocoTestingModule()],
      providers: [
        provideRouter([
          {
            path: 'pl/project-inquiry',
            component: ProjectInquiry,
          },
        ]),
        {
          provide: ProjectInquiryApiService,
          useValue: api
        },
        { provide: ProductCatalogService, useValue: catalog },
      ],
    }).compileComponents();

    harness = await RouterTestingHarness.create();
  });

  async function submitValidInquiry(): Promise<void> {
    const element = harness.routeNativeElement!;

    const values = {
      'inquiry-name': 'Anna',
      'inquiry-email': 'anna@example.com',
      'inquiry-project-type': 'embroideredProduct',
      'inquiry-description': 'Proszę o wycenę personalizacji.',
    };

    for (const [id, value] of Object.entries(values)) {
      const control = element.querySelector<
        HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement
      >(`#${id}`)!;

      control.value = value;
      control.dispatchEvent(new Event('input', { bubbles: true }));

      if (control.tagName === 'SELECT') {
        control.dispatchEvent(new Event('change', { bubbles: true }));
      }
    }

    await harness.fixture.whenStable();

    element
      .querySelector<HTMLFormElement>('form')!
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));

    await harness.fixture.whenStable();
  }

  it.each([
    ['forest-dragon', 'Wzór testowy'],
    ['embroidered-sweatshirt', 'Bluza z haftem'],
  ])('should show the product selected through %s', async (slug, name) => {
    await harness.navigateByUrl(`/pl/project-inquiry?product=${slug}`, ProjectInquiry);

    const element = harness.routeNativeElement!;

    expect(element.querySelector('.project-inquiry__product-context')?.textContent).toContain(name);

    expect(element.querySelector<HTMLTextAreaElement>('#inquiry-description')!.value).toBe('');
  });

  it('should show a general inquiry form without a product parameter', async () => {
    await harness.navigateByUrl('/pl/project-inquiry', ProjectInquiry);

    const element = harness.routeNativeElement!;

    expect(element.querySelector('.project-inquiry__product-context')).toBeNull();

    expect(element.querySelector('form')).not.toBeNull();
  });

  it('should ignore an unknown product and keep the form available', async () => {
    await harness.navigateByUrl('/pl/project-inquiry?product=unknown-product', ProjectInquiry);

    const element = harness.routeNativeElement!;

    expect(element.querySelector('.project-inquiry__product-context')).toBeNull();

    expect(element.querySelector('form')).not.toBeNull();
  });

  it('should submit the product id from the current catalog', async () => {
    await harness.navigateByUrl(
      '/pl/project-inquiry?product=embroidered-sweatshirt',
      ProjectInquiry,
    );

    await submitValidInquiry();

    expect(api.createInquiry).toHaveBeenCalledExactlyOnceWith({
      language: 'pl',
      name: 'Anna',
      email: 'anna@example.com',
      projectType: 'embroideredProduct',
      description: 'Proszę o wycenę personalizacji.',
      inspirationUrl: null,
      productId: 'embroidered-002',
    });
  });

  it('should wait for the catalog before submitting a product inquiry', async () => {
    const products = catalog.products();

    catalog.state.set({ status: 'loading' });

    await harness.navigateByUrl(
      '/pl/project-inquiry?product=embroidered-sweatshirt',
      ProjectInquiry,
    );

    await submitValidInquiry();

    expect(api.createInquiry).not.toHaveBeenCalled();
    expect(harness.routeNativeElement!.querySelector('[role="alert"]')?.textContent).toContain(
      'Trwa sprawdzanie wybranego produktu.',
    );

    catalog.state.set({ status: 'ready', products });

    await harness.fixture.whenStable();
    await submitValidInquiry();

    expect(api.createInquiry).toHaveBeenCalledOnce();
    expect(api.createInquiry.mock.calls[0][0].productId).toBe('embroidered-002');
  });

  it('should not submit a product inquiry when the catalog is unavailable', async () => {
    catalog.state.set({ status: 'error' });

    await harness.navigateByUrl(
      '/pl/project-inquiry?product=embroidered-sweatshirt',
      ProjectInquiry,
    );

    await submitValidInquiry();

    expect(api.createInquiry).not.toHaveBeenCalled();
    expect(harness.routeNativeElement!.querySelector('[role="alert"]')?.textContent).toContain(
      'Nie udało się sprawdzić wybranego produktu.',
    );
  });

  it('should not silently submit a general inquiry for an unknown product', async () => {
    await harness.navigateByUrl('/pl/project-inquiry?product=unknown-product', ProjectInquiry);

    await submitValidInquiry();

    expect(api.createInquiry).not.toHaveBeenCalled();
    expect(harness.routeNativeElement!.querySelector('[role="alert"]')?.textContent).toContain(
      'Wybrany produkt jest niedostępny.',
    );
  });
});
