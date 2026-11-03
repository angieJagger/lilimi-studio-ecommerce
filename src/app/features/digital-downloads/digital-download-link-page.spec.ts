import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { BehaviorSubject, of, Subject, throwError } from 'rxjs';
import { vi } from 'vitest';
import { getTranslocoTestingModule } from '../../testing/transloco-testing';
import { DigitalDownloadLinkApiService } from './digital-download-link-api.service';
import { DigitalDownloadLinkPage } from './digital-download-link-page';
import type { DigitalOrderDownload } from './digital-order-download.model';

describe('DigitalDownloadLinkPage', () => {
  let fixture: ComponentFixture<DigitalDownloadLinkPage>;
  let element: HTMLElement;
  let fragment: BehaviorSubject<string | null>;

  const token = 'a'.repeat(43);

  const file: DigitalOrderDownload = {
    itemId: '97c40491-b22a-4362-a347-87389785bfd5',
    productName: 'Leśny smok',
    filename: 'dragon.zip',
    packageVersion: 1,
    byteSize: 2048,
  };

  const api = {
    getDownloads: vi.fn(),
    download: vi.fn(),
  };

  beforeEach(async () => {
    fragment = new BehaviorSubject<string | null>(null);

    api.getDownloads.mockReset();
    api.getDownloads.mockReturnValue(of([file]));
    api.download.mockReset();

    await TestBed.configureTestingModule({
      imports: [DigitalDownloadLinkPage, getTranslocoTestingModule()],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            fragment: fragment.asObservable(),
          },
        },
        {
          provide: DigitalDownloadLinkApiService,
          useValue: api,
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DigitalDownloadLinkPage);
    element = fixture.nativeElement;
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('should save the ZIP after clicking download', async () => {
    fragment.next(`token=${token}`);

    await fixture.whenStable();

    const blob = new Blob(['ZIP content'], { type: 'application/zip' });

    api.download.mockReturnValue(of(new HttpResponse({ body: blob })));

    const createObjectURL = vi.fn().mockReturnValue('blob:test-download');

    const revokeObjectURL = vi.fn();

    const OriginalURL = URL;

    vi.stubGlobal(
      'URL',
      class extends OriginalURL {
        static override createObjectURL = createObjectURL;
        static override revokeObjectURL = revokeObjectURL;
      },
    );

    let clickedAnchor: HTMLAnchorElement | undefined;

    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (
      this: HTMLAnchorElement,
    ) {
      clickedAnchor = this;
    });

    element.querySelector<HTMLButtonElement>('.download-link__file button')!.click();

    await fixture.whenStable();

    expect(api.download).toHaveBeenCalledExactlyOnceWith(token, file.itemId);

    expect(createObjectURL).toHaveBeenCalledExactlyOnceWith(blob);
    expect(clickedAnchor?.download).toBe(file.filename);
    expect(clickedAnchor?.getAttribute('href')).toBe('blob:test-download');
    expect(clickedAnchor?.isConnected).toBe(false);
  });

  it('should prevent duplicate requests and allow retry after failure', async () => {
    fragment.next(`token=${token}`);

    await fixture.whenStable();

    const response = new Subject<HttpResponse<Blob>>();

    api.download.mockReturnValue(response.asObservable());

    const button = element.querySelector<HTMLButtonElement>('.download-link__file button')!;

    button.click();
    button.click();

    await fixture.whenStable();

    expect(api.download).toHaveBeenCalledTimes(1);
    expect(button.disabled).toBe(true);

    response.error(new HttpErrorResponse({ status: 503 }));

    await fixture.whenStable();

    expect(button.disabled).toBe(false);
    expect(element.querySelector('[role="alert"]')?.textContent).toContain(
      'Nie udało się pobrać pliku',
    );

    api.download.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 503 })));

    button.click();

    await fixture.whenStable();

    expect(api.download).toHaveBeenCalledTimes(2);
  });

  it('should reject a missing token without calling the API', async () => {
    await fixture.whenStable();

    expect(element.querySelector('[role="alert"]')?.textContent).toContain(
      'Link jest nieprawidłowy',
    );

    expect(api.getDownloads).not.toHaveBeenCalled();
    expect(api.download).not.toHaveBeenCalled();
  });

  it('should load files using the fragment token', async () => {
    fragment.next(`token=${token}`);

    await fixture.whenStable();

    expect(api.getDownloads).toHaveBeenCalledExactlyOnceWith(token);

    expect(element.textContent).toContain(file.productName);
    expect(element.textContent).toContain(file.filename);
    expect(element.textContent).not.toContain(token);
    expect(element.querySelector('[role="alert"]')).toBeNull();

    // Opening the link must not automatically download a ZIP.
    expect(api.download).not.toHaveBeenCalled();
  });

  it('should reject malformed and duplicate tokens', async () => {
    fragment.next('token=invalid');

    await fixture.whenStable();

    expect(element.querySelector('[role="alert"]')).not.toBeNull();
    expect(api.getDownloads).not.toHaveBeenCalled();

    fragment.next(`token=${token}&token=${token}`);

    await fixture.whenStable();

    expect(element.querySelector('[role="alert"]')).not.toBeNull();
    expect(api.getDownloads).not.toHaveBeenCalled();
  });

  it('should show an invalid-link message when the API rejects access', async () => {
    api.getDownloads.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));

    fragment.next(`token=${token}`);

    await fixture.whenStable();

    expect(element.querySelector('[role="alert"]')?.textContent).toContain(
      'Link jest nieprawidłowy',
    );

    expect(element.querySelector('.download-link__files')).toBeNull();
    expect(api.download).not.toHaveBeenCalled();
  });
});
