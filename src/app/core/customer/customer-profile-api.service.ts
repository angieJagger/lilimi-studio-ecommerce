import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { switchMap, type Observable } from 'rxjs';
import { API_BASE_URL } from '../api/api-base-url';
import { CsrfService } from '../auth/csrf.service';
import type { CustomerProfile, UpdateCustomerProfileRequest } from './customer-profile.model';

@Injectable({
  providedIn: 'root',
})
export class CustomerProfileApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);
  private readonly csrf = inject(CsrfService);

  getProfile(): Observable<CustomerProfile> {
    return this.http.get<CustomerProfile>(`${this.baseUrl}/account/profile`);
  }

  updateProfile(request: UpdateCustomerProfileRequest): Observable<CustomerProfile> {
    return this.csrf
      .initialize()
      .pipe(
        switchMap(() => this.http.put<CustomerProfile>(`${this.baseUrl}/account/profile`, request)),
      );
  }
}
