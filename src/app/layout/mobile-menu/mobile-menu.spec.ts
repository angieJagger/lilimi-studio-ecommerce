import { computed, signal, WritableSignal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslocoService } from '@jsverse/transloco';
import type { CurrentUser } from '../../core/auth/auth.model';
import { AuthSessionService } from '../../core/auth/auth-session.service';
import { getTranslocoTestingModule } from '../../testing/transloco-testing';
import { MobileMenu } from './mobile-menu';

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

describe('MobileMenu', () => {
  let fixture: ComponentFixture<MobileMenu>;
  let currentUser: WritableSignal<CurrentUser | null>;

  beforeEach(async () => {
    currentUser = signal<CurrentUser | null>(null);

    const session = {
      user: currentUser.asReadonly(),
      isAuthenticated: computed(() => currentUser() !== null),
      isAdmin: computed(() => currentUser()?.roles.includes('ADMIN') ?? false),
    };

    await TestBed.configureTestingModule({
      imports: [MobileMenu, getTranslocoTestingModule()],
      providers: [
        provideRouter([
          {
            path: ':language/account/login',
            children: [],
          },
        ]),
        { provide: AuthSessionService, useValue: session },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(MobileMenu);
    await fixture.whenStable();
  });

  function accountLinks(): NodeListOf<HTMLAnchorElement> {
    return fixture.nativeElement.querySelectorAll('.mobile-menu__links a[href*="/account/login"]');
  }

  it.each(accountCases)(
    'should display exactly one account link for $name',
    async ({ user, pl, en }) => {
      currentUser.set(user);

      for (const language of ['pl', 'en'] as const) {
        TestBed.inject(TranslocoService).setActiveLang(language);
        fixture.detectChanges();
        await fixture.whenStable();

        const links = accountLinks();

        expect(links.length).toBe(1);
        expect(links[0].textContent?.trim()).toBe(language === 'pl' ? pl : en);
        expect(links[0].getAttribute('href')).toBe(`/${language}/account/login`);
      }
    },
  );

  it('should close the menu when the account link is clicked', async () => {
    const component = fixture.componentInstance;
    const close = vi.spyOn(component, 'close').mockImplementation(() => {});

    accountLinks()[0].click();
    await fixture.whenStable();

    expect(close).toHaveBeenCalledOnce();
  });
});
