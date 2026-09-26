package pl.lilimi.catalog;

import java.util.UUID;

public interface GarmentVariantView {

  UUID getId();
  String getPatternId();
  String getFit();
  String getSize();
  String getColor();
  String getEmbroideryOptionId();
  Integer getWidthMm();
  Integer getHeightMm();
  String getPlacement();
  Integer getPriceInGrosz();
}
