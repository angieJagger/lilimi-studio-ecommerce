import { ComponentFixture, TestBed } from '@angular/core/testing';
import { computed, signal, WritableSignal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { TranslocoService } from '@jsverse/transloco';
import { of } from 'rxjs';
import { AuthSessionService } from '../../core/auth/auth-session.service';
import type { CurrentUser } from '../../core/auth/auth.model';
import { getTranslocoTestingModule } from '../../testing/transloco-testing';
import { provideProductCatalogTesting } from '../../testing/product-catalog-testing';
import { provideSweatshirtVariantsTesting } from '../../testing/sweatshirt-variants-testing';
import { Header } from './header';

const accountCases: {
  name: string;
  user: CurrentUser | null;
  pl: string;
  en: string;
}[] = [
  {
    name: 'signed-out visitor',
    user: null,
    pl: 'Zaloguj',
    en: 'Sign in',
  },
  {
    name: 'customer with a name',
    user: {
      email: 'customer@example.com',
      roles: ['CUSTOMER'],
      firstName: 'Anna',
    },
    pl: 'Anna',
    en: 'Anna',
  },
  {
    name: 'customer without a name',
    user: {
      email: 'customer@example.com',
      roles: ['CUSTOMER'],
      firstName: null,
    },
    pl: 'Użytkownik',
    en: 'User',
  },
  {
    name: 'customer with a blank name',
    user: {
      email: 'customer@example.com',
      roles: ['CUSTOMER'],
      firstName: '   ',
    },
    pl: 'Użytkownik',
    en: 'User',
  },
  {
    name: 'administrator with a name',
    user: {
      email: 'admin@example.com',
      roles: ['ADMIN'],
      firstName: 'Anna',
    },
    pl: 'Admin',
    en: 'Admin',
  },
];

describe('Header', () => {
  let fixture: ComponentFixture<Header>;
  let currentUser: WritableSignal<CurrentUser | null>;

  beforeEach(async () => {
    currentUser = signal<CurrentUser | null>(null);

    const session = {
      user: currentUser.asReadonly(),
      isAuthenticated: computed(() => currentUser() !== null),
      isAdmin: computed(() => currentUser()?.roles.includes('ADMIN') ?? false),
      refresh: vi.fn(() => of(currentUser())),
    };

    await TestBed.configureTestingModule({
      imports: [Header, getTranslocoTestingModule()],
      providers: [
        provideRouter([]),
        provideSweatshirtVariantsTesting(),
        provideProductCatalogTesting(),
        { provide: AuthSessionService, useValue: session },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Header);
    await fixture.whenStable();
  });

  function accountLink(): HTMLAnchorElement {
    return fixture.nativeElement.querySelector('.site-header__account') as HTMLAnchorElement;
  }

  it.each(accountCases)(
    'should display the correct account link for $name',
    async ({ user, pl, en }) => {
      currentUser.set(user);

      for (const language of ['pl', 'en'] as const) {
        TestBed.inject(TranslocoService).setActiveLang(language);
        fixture.detectChanges();
        await fixture.whenStable();

        const link = accountLink();

        expect(link).not.toBeNull();
        expect(link.textContent?.trim()).toBe(language === 'pl' ? pl : en);
        expect(link.getAttribute('href')).toBe(`/${language}/account/login`);
      }
    },
  );

  it('should update the link after login, name change and logout', async () => {
    TestBed.inject(TranslocoService).setActiveLang('pl');
    fixture.detectChanges();
    await fixture.whenStable();

    expect(accountLink().textContent?.trim()).toBe('Zaloguj');

    currentUser.set({
      email: 'customer@example.com',
      roles: ['CUSTOMER'],
      firstName: 'Anna',
    });
    fixture.detectChanges();
    await fixture.whenStable();

    expect(accountLink().textContent?.trim()).toBe('Anna');

    currentUser.update((user) => (user ? { ...user, firstName: 'Maria' } : null));
    fixture.detectChanges();
    await fixture.whenStable();

    expect(accountLink().textContent?.trim()).toBe('Maria');

    currentUser.set(null);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(accountLink().textContent?.trim()).toBe('Zaloguj');
    expect(accountLink().getAttribute('href')).toBe('/pl/account/login');
  });
});
