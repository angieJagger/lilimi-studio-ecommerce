import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { switchMap, type Observable } from 'rxjs';
import { API_BASE_URL } from '../../../core/api/api-base-url';
import { CsrfService } from '../../../core/auth/csrf.service';
import type {
  AdminProductPage,
  AdminProductSummary,
  ChangeProductVisibilityRequest,
} from './admin-product.model';

@Injectable({
  providedIn: 'root',
})
export class AdminProductApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);
  private readonly csrf = inject(CsrfService);

  getProducts(page = 0, size = 20): Observable<AdminProductPage> {
    const params = new HttpParams().set('page', page).set('size', size);

    return this.http.get<AdminProductPage>(`${this.baseUrl}/admin/products`, { params });
  }

  changeVisibility(
    id: string,
    request: ChangeProductVisibilityRequest,
  ): Observable<AdminProductSummary> {
    return this.csrf
      .initialize()
      .pipe(
        switchMap(() =>
          this.http.patch<AdminProductSummary>(
            `${this.baseUrl}/admin/products/${encodeURIComponent(id)}/visibility`,
            request,
          ),
        ),
      );
  }
}
