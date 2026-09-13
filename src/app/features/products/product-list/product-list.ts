import { Component } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

@Component({
  imports: [ TranslocoPipe ],
  selector: 'app-product-list',
  styleUrl: './product-list.scss',
  templateUrl: './product-list.html',
})
export class ProductList {}
