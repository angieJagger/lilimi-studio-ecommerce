import { provideRouter } from '@angular/router';
import { getTranslocoTestingModule } from '../../testing/transloco-testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideSweatshirtVariantsTesting } from '../../testing/sweatshirt-variants-testing';
import { Header } from './header';
import { provideProductCatalogTesting } from '../../testing/product-catalog-testing';
import { TranslocoService } from '@jsverse/transloco';

describe('Header', () => {
  let component: Header;
  let fixture: ComponentFixture<Header>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Header, getTranslocoTestingModule()],
      providers: [
        provideRouter([]),
        provideSweatshirtVariantsTesting(),
        provideProductCatalogTesting(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Header);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
  it.each(['pl', 'en'])(
    'should link to the account page without encoding the slash for %s',
    async (language) => {
      TestBed.inject(TranslocoService).setActiveLang(language);
      fixture.detectChanges();
      await fixture.whenStable();

      const element = fixture.nativeElement as HTMLElement;

      const accountLink = element.querySelector<HTMLAnchorElement>(
        '.site-header__actions a[href*="account"]',
      );

      expect(accountLink).not.toBeNull();
      expect(accountLink?.getAttribute('href')).toBe(`/${language}/account/login`);
    },
  );
});
