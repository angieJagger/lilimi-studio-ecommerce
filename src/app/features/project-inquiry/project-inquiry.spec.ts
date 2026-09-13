import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProjectInquiry } from './project-inquiry';
import { getTranslocoTestingModule } from '../../testing/transloco-testing';

describe('ProjectInquiry', () => {
  let component: ProjectInquiry;
  let fixture: ComponentFixture<ProjectInquiry>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProjectInquiry, getTranslocoTestingModule()],
    }).compileComponents();

    fixture = TestBed.createComponent(ProjectInquiry);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
