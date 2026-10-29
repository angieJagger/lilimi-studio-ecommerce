import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import type { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/api/api-base-url';
import type { DigitalOrderDownload } from './digital-order-download.model';

@Injectable({
  providedIn: 'root',
})
export class DigitalOrderDownloadApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  getDownloads(orderId: string): Observable<readonly DigitalOrderDownload[]> {
    return this.http.get<readonly DigitalOrderDownload[]>(`${this.orderUrl(orderId)}/downloads`);
  }

  downloadUrl(orderId: string, itemId: string): string {
    return `${this.orderUrl(orderId)}/items/${encodeURIComponent(itemId)}/download`;
  }

  private orderUrl(orderId: string): string {
    return `${this.baseUrl}/orders/${encodeURIComponent(orderId)}`;
  }
}
