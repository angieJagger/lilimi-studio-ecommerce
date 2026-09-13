import { Component } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

@Component({
  imports: [TranslocoPipe],
  selector: 'app-project-inquiry',
  styleUrl: './project-inquiry.scss',
  templateUrl: './project-inquiry.html',
})
export class ProjectInquiry {}
