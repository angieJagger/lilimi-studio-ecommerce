import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { switchMap, type Observable } from 'rxjs';
import { API_BASE_URL } from '../../../core/api/api-base-url';
import { CsrfService } from '../../../core/auth/csrf.service';
import type { AdminDigitalProductPackage } from './admin-digital-product-package.model';

@Injectable({
  providedIn: 'root',
})
export class AdminDigitalProductPackageApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);
  private readonly csrf = inject(CsrfService);

  getPackages(productId: string): Observable<readonly AdminDigitalProductPackage[]> {
    return this.http.get<readonly AdminDigitalProductPackage[]>(this.packagesUrl(productId));
  }

  upload(productId: string, file: File): Observable<AdminDigitalProductPackage> {
    const body = new FormData();
    body.append('file', file);

    return this.csrf
      .initialize()
      .pipe(
        switchMap(() =>
          this.http.post<AdminDigitalProductPackage>(this.packagesUrl(productId), body),
        ),
      );
  }

  private packagesUrl(productId: string): string {
    return `${this.baseUrl}/admin/products/${encodeURIComponent(productId)}/digital-packages`;
  }
}
