import { provideRouter } from '@angular/router';
import { getTranslocoTestingModule } from '../../testing/transloco-testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MobileMenu } from './mobile-menu';

describe('MobileMenu', () => {
  let component: MobileMenu;
  let fixture: ComponentFixture<MobileMenu>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MobileMenu, getTranslocoTestingModule()],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(MobileMenu);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
