import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { of, Subject } from 'rxjs';
import { vi } from 'vitest';
import { getTranslocoTestingModule } from '../../../testing/transloco-testing';
import { AdminDigitalProductPackages } from './admin-digital-product-packages';
import { AdminDigitalProductPackageApiService } from './admin-digital-product-package-api.service';
import type { AdminDigitalProductPackage } from './admin-digital-product-package.model';

describe('AdminDigitalProductPackages', () => {
  let fixture: ComponentFixture<AdminDigitalProductPackages>;

  const savedPackage: AdminDigitalProductPackage = {
    id: 'package-1',
    productId: 'pattern-001',
    packageVersion: 1,
    originalFilename: 'dragon.zip',
    byteSize: 1024,
    sha256: 'a'.repeat(64),
    currentPackage: true,
    createdAt: '2026-10-29T10:00:00Z',
  };

  const api = {
    getPackages: vi.fn(),
    upload: vi.fn(),
  };

  beforeEach(async () => {
    api.getPackages.mockReset().mockReturnValue(of([]));
    api.upload.mockReset();

    await TestBed.configureTestingModule({
      imports: [AdminDigitalProductPackages, getTranslocoTestingModule()],
      providers: [
        {
          provide: AdminDigitalProductPackageApiService,
          useValue: api,
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AdminDigitalProductPackages);
    fixture.componentRef.setInput('productId', 'pattern-001');
    await fixture.whenStable();
  });

  function uploadButton(): HTMLButtonElement {
    return fixture.nativeElement.querySelector('.digital-packages__upload button');
  }

  async function selectFile(file: File): Promise<void> {
    const input = fixture.nativeElement.querySelector('input[type="file"]') as HTMLInputElement;

    Object.defineProperty(input, 'files', {
      configurable: true,
      value: [file],
    });

    input.dispatchEvent(new Event('change'));
    await fixture.whenStable();
  }

  it('should load history and disable upload without a selected file', () => {
    expect(api.getPackages).toHaveBeenCalledWith('pattern-001');
    expect(uploadButton().disabled).toBe(true);
  });

  it.each([
    ['photo.png', 'content'],
    ['empty.zip', ''],
  ])('should reject an invalid file: %s', async (name, content) => {
    await selectFile(new File([content], name));

    expect(uploadButton().disabled).toBe(true);
    expect(fixture.nativeElement.querySelector('#digital-package-file-error')).not.toBeNull();
    expect(api.upload).not.toHaveBeenCalled();
  });

  it('should reject files larger than 10 MiB', async () => {
    const file = new File(['content'], 'large.zip');

    Object.defineProperty(file, 'size', {
      value: 10 * 1024 * 1024 + 1,
    });

    await selectFile(file);

    expect(uploadButton().disabled).toBe(true);
    expect(api.upload).not.toHaveBeenCalled();
  });

  it('should prevent duplicate uploads and refresh history after success', async () => {
    const pending = new Subject<AdminDigitalProductPackage>();
    api.upload.mockReturnValue(pending);

    const file = new File(['ZIP content'], 'dragon.zip');
    await selectFile(file);

    uploadButton().click();
    uploadButton().click();
    await fixture.whenStable();

    expect(api.upload).toHaveBeenCalledTimes(1);
    expect(api.upload).toHaveBeenCalledWith('pattern-001', file);
    expect(uploadButton().disabled).toBe(true);

    api.getPackages.mockReturnValue(of([savedPackage]));
    pending.next(savedPackage);
    pending.complete();
    await fixture.whenStable();

    expect(api.getPackages).toHaveBeenCalledTimes(2);
    expect(fixture.nativeElement.querySelector('.digital-packages__item')?.textContent).toContain(
      'dragon.zip',
    );
    expect(fixture.nativeElement.querySelector('.digital-packages__success')).not.toBeNull();
    expect(uploadButton().disabled).toBe(true);
  });

  it('should show an upload error without reporting success', async () => {
    const pending = new Subject<AdminDigitalProductPackage>();
    api.upload.mockReturnValue(pending);

    await selectFile(new File(['content'], 'dragon.zip'));
    uploadButton().click();

    pending.error(
      new HttpErrorResponse({
        status: 400,
        error: { code: 'DIGITAL_PACKAGE_INVALID' },
      }),
    );
    await fixture.whenStable();

    expect(
      fixture.nativeElement.querySelector('.digital-packages__upload .digital-packages__error'),
    ).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.digital-packages__success')).toBeNull();
    expect(uploadButton().disabled).toBe(false);
  });

  it('should block another upload until history is refreshed after an uncertain result', async () => {
    const pendingUpload = new Subject<AdminDigitalProductPackage>();
    const pendingHistory = new Subject<readonly AdminDigitalProductPackage[]>();

    api.upload.mockReturnValue(pendingUpload);

    await selectFile(new File(['ZIP content'], 'dragon.zip'));
    uploadButton().click();

    pendingUpload.error(
      new HttpErrorResponse({
        status: 0,
        statusText: 'Unknown Error',
      }),
    );
    await fixture.whenStable();

    expect(uploadButton().disabled).toBe(true);

    const refreshButton = fixture.nativeElement.querySelector(
      '.digital-packages__upload .button--secondary',
    ) as HTMLButtonElement;

    expect(refreshButton).not.toBeNull();

    // Wybór innego pliku nie może zdejmować blokady.
    await selectFile(new File(['Other ZIP content'], 'another.zip'));

    expect(uploadButton().disabled).toBe(true);
    expect(api.upload).toHaveBeenCalledTimes(1);

    api.getPackages.mockReturnValue(pendingHistory);
    refreshButton.click();
    await fixture.whenStable();

    expect(uploadButton().disabled).toBe(true);
    expect(api.getPackages).toHaveBeenCalledTimes(2);

    // Historia może potwierdzić, że zapis jednak się powiódł.
    pendingHistory.next([savedPackage]);
    pendingHistory.complete();
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('.digital-packages__item')?.textContent).toContain(
      'dragon.zip',
    );

    expect(uploadButton().disabled).toBe(false);
    expect(
      fixture.nativeElement.querySelector('.digital-packages__upload .button--secondary'),
    ).toBeNull();
  });

  it('should keep uploads blocked when refreshing history fails', async () => {
    const pendingUpload = new Subject<AdminDigitalProductPackage>();
    const pendingHistory = new Subject<readonly AdminDigitalProductPackage[]>();

    api.upload.mockReturnValue(pendingUpload);

    await selectFile(new File(['ZIP content'], 'dragon.zip'));
    uploadButton().click();

    pendingUpload.error(
      new HttpErrorResponse({
        status: 504,
        statusText: 'Gateway Timeout',
      }),
    );
    await fixture.whenStable();

    api.getPackages.mockReturnValue(pendingHistory);

    const refreshButton = fixture.nativeElement.querySelector(
      '.digital-packages__upload .button--secondary',
    ) as HTMLButtonElement;

    refreshButton.click();
    await fixture.whenStable();

    pendingHistory.error(
      new HttpErrorResponse({
        status: 503,
        statusText: 'Service Unavailable',
      }),
    );
    await fixture.whenStable();

    expect(uploadButton().disabled).toBe(true);
    expect(api.upload).toHaveBeenCalledTimes(1);

    expect(
      fixture.nativeElement.querySelector('.digital-packages__feedback [role="alert"]'),
    ).not.toBeNull();

    // Dopiero kolejne, udane odświeżenie może zdjąć blokadę.
    api.getPackages.mockReturnValue(of([savedPackage]));

    const retryButton = fixture.nativeElement.querySelector(
      '.digital-packages__feedback button',
    ) as HTMLButtonElement;

    retryButton.click();
    await fixture.whenStable();

    expect(uploadButton().disabled).toBe(false);
    expect(api.getPackages).toHaveBeenCalledTimes(3);
  });
});