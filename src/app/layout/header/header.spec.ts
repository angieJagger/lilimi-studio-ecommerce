import { provideRouter } from '@angular/router';
import { getTranslocoTestingModule } from '../../testing/transloco-testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideSweatshirtVariantsTesting } from '../../testing/sweatshirt-variants-testing';
import { Header } from './header';
import { provideProductCatalogTesting } from '../../testing/product-catalog-testing';

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
});
