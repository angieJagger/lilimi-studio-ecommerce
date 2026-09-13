import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { Header } from '../header/header';
import { Footer } from '../footer/footer';

@Component({
  imports: [RouterOutlet, Header, Footer],
  selector: 'app-shop-layout',
  styleUrl: './shop-layout.scss',
  templateUrl: './shop-layout.html',
})
export class ShopLayout {}
