import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { getTranslocoTestingModule } from '../../testing/transloco-testing';
import { DigitalOrderDownloadApiService } from './digital-order-download-api.service';
import { DigitalOrderDownloads } from './digital-order-downloads';
import type { DigitalOrderDownload } from './digital-order-download.model';

describe('DigitalOrderDownloads', () => {
  let fixture: ComponentFixture<DigitalOrderDownloads>;
  let element: HTMLElement;

  const orderId = '93af42f1-d557-4b3d-b452-24976e049713';

  const file: DigitalOrderDownload = {
    itemId: '97c40491-b22a-4362-a347-87389785bfd5',
    productName: 'Wzór haftu „Leśny smok”',
    filename: 'lesny-smok.zip',
    packageVersion: 2,
    byteSize: 2048,
  };

  const downloadUrl = `/api/orders/${orderId}/items/${file.itemId}/download`;

  const api = {
    getDownloads: vi.fn(),
    downloadUrl: vi.fn(),
  };

  beforeEach(async () => {
    api.getDownloads.mockReset();
    api.getDownloads.mockReturnValue(of([file]));

    api.downloadUrl.mockReset();
    api.downloadUrl.mockReturnValue(downloadUrl);

    await TestBed.configureTestingModule({
      imports: [DigitalOrderDownloads, getTranslocoTestingModule()],
      providers: [
        {
          provide: DigitalOrderDownloadApiService,
          useValue: api,
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DigitalOrderDownloads);
    fixture.componentRef.setInput('orderId', orderId);
    element = fixture.nativeElement;
  });

  it('should display the assigned package and its download link', async () => {
    await fixture.whenStable();

    expect(api.getDownloads).toHaveBeenCalledExactlyOnceWith(orderId);

    expect(element.textContent).toContain(file.productName);
    expect(element.textContent).toContain(file.filename);
    expect(element.textContent).toContain('2 KiB');

    expect(element.querySelector('a')?.getAttribute('href')).toBe(downloadUrl);

    expect(api.downloadUrl).toHaveBeenCalledWith(orderId, file.itemId);
  });

  it('should not display a download link when access is denied', async () => {
    api.getDownloads.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));

    await fixture.whenStable();

    expect(element.querySelector('[role="alert"]')).not.toBeNull();
    expect(element.querySelector('a')).toBeNull();
    expect(api.downloadUrl).not.toHaveBeenCalled();
  });

  it('should retry loading after a connection failure', async () => {
    api.getDownloads.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 0 })));

    await fixture.whenStable();

    expect(element.querySelector('[role="alert"]')).not.toBeNull();

    api.getDownloads.mockReturnValue(of([file]));

    element.querySelector<HTMLButtonElement>('button')!.click();

    await fixture.whenStable();

    expect(api.getDownloads).toHaveBeenCalledTimes(2);
    expect(element.querySelector('[role="alert"]')).toBeNull();
    expect(element.querySelector('a')?.getAttribute('href')).toBe(downloadUrl);
  });

  it('should not display a download link when no packages are available', async () => {
    api.getDownloads.mockReturnValue(of([]));

    await fixture.whenStable();

    expect(element.querySelector('a')).toBeNull();
    expect(element.querySelector('[role="alert"]')).toBeNull();
    expect(element.querySelectorAll('li')).toHaveLength(1);
    expect(api.downloadUrl).not.toHaveBeenCalled();
  });
});
