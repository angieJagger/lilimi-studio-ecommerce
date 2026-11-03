import { HttpClient, HttpHeaders } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { API_BASE_URL } from '../../core/api/api-base-url';
import type { DigitalOrderDownload } from './digital-order-download.model';
import { switchMap } from 'rxjs';
import { CsrfService } from '../../core/auth/csrf.service';

@Injectable({
  providedIn: 'root',
})
export class DigitalDownloadLinkApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);
  private readonly csrf = inject(CsrfService);

  getDownloads(rawToken: string) {
    return this.http.get<readonly DigitalOrderDownload[]>(`${this.baseUrl}/digital-downloads`, {
      headers: this.tokenHeaders(rawToken),
    });
  }

  download(rawToken: string, itemId: string) {
    return this.http.get(
      `${this.baseUrl}/digital-downloads/items/` + `${encodeURIComponent(itemId)}/download`,
      {
        headers: this.tokenHeaders(rawToken),
        responseType: 'blob',
        observe: 'response',
      },
    );
  }

  resend(orderId: string, email: string) {
    return this.csrf.initialize().pipe(
      switchMap(() =>
        this.http.post<void>(`${this.baseUrl}/digital-downloads/resend`, {
          orderId,
          email,
        }),
      ),
    );
  }

  private tokenHeaders(rawToken: string): HttpHeaders {
    return new HttpHeaders({
      'X-Digital-Download-Token': rawToken,
    });
  }
}
