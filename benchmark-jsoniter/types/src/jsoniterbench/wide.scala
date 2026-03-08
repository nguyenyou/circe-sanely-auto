package jsoniterbench

// Wide case classes (stress field count) + extra types

case class Wide16A(f1: String, f2: Int, f3: Double, f4: Boolean, f5: String, f6: Int, f7: Double, f8: Boolean, f9: String, f10: Int, f11: String, f12: Int, f13: Double, f14: Boolean, f15: String, f16: Int)
case class Wide16B(g1: String, g2: Int, g3: Double, g4: Boolean, g5: String, g6: Int, g7: Double, g8: Boolean, g9: String, g10: Int, g11: String, g12: Int, g13: Double, g14: Boolean, g15: String, g16: Int)
case class Wide22A(h1: String, h2: Int, h3: Double, h4: Boolean, h5: String, h6: Int, h7: Double, h8: Boolean, h9: String, h10: Int, h11: String, h12: Int, h13: Double, h14: Boolean, h15: String, h16: Int, h17: Double, h18: Boolean, h19: String, h20: Int, h21: Double, h22: Boolean)
case class Wide22B(i1: String, i2: Int, i3: Double, i4: Boolean, i5: String, i6: Int, i7: Double, i8: Boolean, i9: String, i10: Int, i11: String, i12: Int, i13: Double, i14: Boolean, i15: String, i16: Int, i17: Double, i18: Boolean, i19: String, i20: Int, i21: Double, i22: Boolean)
case class Config22(host: String, port: Int, database: String, username: String, password: String, ssl: Boolean, maxConn: Int, minConn: Int, timeout: Int, retries: Int, cacheTtl: Int, logLevel: String, region: String, zone: String, instanceType: String, memoryMb: Int, cpuCores: Int, diskGb: Int, networkBandwidth: Int, backupEnabled: Boolean, monitoringEnabled: Boolean, env: String)
case class UserProfile22(firstName: String, lastName: String, upEmail: String, phone: String, company: String, title: String, bio: String, avatar: String, upWebsite: String, twitter: String, github: String, linkedin: String, location: String, upTimezone: String, upLanguage: String, upTheme: String, upFontSize: Int, notifications: Boolean, newsletter: Boolean, twoFactor: Boolean, lastLogin: String, upCreatedAt: String)
case class OrderDetail22(orderId: String, customerId: String, productName: String, odSku: String, quantity: Int, unitPrice: Double, discount: Double, tax: Double, shipping: Double, total: Double, odCurrency: String, odStatus: String, paymentMethod: String, shippingMethod: String, trackingNumber: String, warehouse: String, odWeight: Double, notes: String, couponCode: String, giftWrap: Boolean, odPriority: Int, estimatedDelivery: String)
case class EventLog22(eventId: String, elTimestamp: String, eventType: String, source: String, elUserId: String, elSessionId: String, ipAddress: String, userAgent: String, url: String, referrer: String, action: String, target: String, elValue: Double, elDuration: Int, statusCode: Int, errorMessage: String, stackTrace: String, elTags: String, metadata: String, processed: Boolean, retryCount: Int, elRegion: String)

// Additional simple types
case class Rgb(r: Int, g: Int, b: Int)
case class Hsl(h: Double, s: Double, l: Double)
case class Vec2(x: Double, y: Double)
case class Vec3(x: Double, y: Double, z: Double)
case class Matrix2x2(m00: Double, m01: Double, m10: Double, m11: Double)
case class DateRange(start: String, end: String)
case class TimeSlot(day: String, startHour: Int, endHour: Int)
case class SemVer(major: Int, minor: Int, patch: Int)
case class SemVerFull(version: SemVer, preRelease: Option[String], buildMeta: Option[String])
case class ConnPool(minSize: Int, maxSize: Int, idleTimeoutMs: Int, acquireTimeoutMs: Int, validationQuery: String)
case class DbConfig(host: String, port: Int, database: String, username: String, ssl: Boolean, pool: ConnPool)
case class CacheConfig(host: String, port: Int, database: Int, maxRetries: Int, timeoutMs: Int)
