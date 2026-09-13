import { provideRouter } from '@angular/router';
import { getTranslocoTestingModule } from '../../testing/transloco-testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ShopLayout } from './shop-layout';

describe('ShopLayout', () => {
  let component: ShopLayout;
  let fixture: ComponentFixture<ShopLayout>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ShopLayout, getTranslocoTestingModule()],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(ShopLayout);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
