# Supabase Setup Guide

## Step 1: Create Supabase Project

1. Go to [Supabase Dashboard](https://app.supabase.com)
2. Click "New project"
3. Enter project name: "promo-code-app"
4. Create a strong database password
5. Select region closest to your users
6. Click "Create new project"

## Step 2: Obtain Connection Details

1. Go to Project Settings > API
2. Copy **Project URL** - this is your API endpoint
3. Copy **anon** key - public key for client-side requests
4. Copy **service_role** key - secret key for server-side operations

## Step 3: Create Database Tables

In Supabase SQL Editor, run the following:

```sql
-- Users table
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id TEXT UNIQUE NOT NULL,
    email TEXT UNIQUE NOT NULL,
    first_name TEXT,
    last_name TEXT,
    profile_image_url TEXT,
    fcm_token TEXT,
    default_geofence_radius INT DEFAULT 150,
    notifications_enabled BOOLEAN DEFAULT TRUE,
    proximity_notifications_enabled BOOLEAN DEFAULT TRUE,
    expiration_notifications_enabled BOOLEAN DEFAULT TRUE,
    membership_notifications_enabled BOOLEAN DEFAULT TRUE,
    location_permission_granted BOOLEAN DEFAULT FALSE,
    background_location_permission_granted BOOLEAN DEFAULT FALSE,
    created_date BIGINT NOT NULL,
    last_modified BIGINT NOT NULL,
    last_sync_date BIGINT
);

-- Coupons table
CREATE TABLE coupons (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id TEXT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    code TEXT NOT NULL,
    merchant_name TEXT NOT NULL,
    discount_type TEXT NOT NULL, -- PERCENTAGE, FIXED_AMOUNT, BOGO, FREE_SHIPPING
    discount_value DECIMAL(10,2) NOT NULL,
    discount_value_currency TEXT DEFAULT 'USD',
    min_purchase_amount DECIMAL(10,2),
    description TEXT,
    expiration_date BIGINT NOT NULL,
    created_date BIGINT NOT NULL,
    category TEXT,
    is_favorite BOOLEAN DEFAULT FALSE,
    is_used BOOLEAN DEFAULT FALSE,
    is_archived BOOLEAN DEFAULT FALSE,
    image_url TEXT,
    barcode_data TEXT,
    notes TEXT,
    last_modified BIGINT NOT NULL
);

-- Coupon Locations table
CREATE TABLE coupon_locations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    coupon_id UUID NOT NULL REFERENCES coupons(id) ON DELETE CASCADE,
    user_id TEXT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    latitude DECIMAL(10,8) NOT NULL,
    longitude DECIMAL(11,8) NOT NULL,
    radius INT DEFAULT 150,
    geofence_id TEXT,
    location_name TEXT,
    created_date BIGINT NOT NULL
);

-- Memberships table
CREATE TABLE memberships (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id TEXT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    organization_name TEXT NOT NULL,
    membership_number TEXT NOT NULL,
    membership_type TEXT,
    start_date BIGINT NOT NULL,
    renewal_date BIGINT NOT NULL,
    annual_fee DECIMAL(10,2),
    monthly_fee DECIMAL(10,2),
    currency TEXT DEFAULT 'USD',
    benefits TEXT,
    notes TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    reminder_enabled BOOLEAN DEFAULT TRUE,
    reminder_days_before_renewal INT DEFAULT 7,
    created_date BIGINT NOT NULL,
    last_modified BIGINT NOT NULL
);

-- Membership Locations table
CREATE TABLE membership_locations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    membership_id UUID NOT NULL REFERENCES memberships(id) ON DELETE CASCADE,
    user_id TEXT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    latitude DECIMAL(10,8) NOT NULL,
    longitude DECIMAL(11,8) NOT NULL,
    radius INT DEFAULT 150,
    location_name TEXT,
    address TEXT,
    created_date BIGINT NOT NULL
);

-- Sync Metadata table
CREATE TABLE sync_metadata (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key TEXT UNIQUE NOT NULL,
    value TEXT NOT NULL,
    last_updated BIGINT NOT NULL
);

-- Create indexes for better query performance
CREATE INDEX idx_coupons_user_id ON coupons(user_id);
CREATE INDEX idx_coupons_expiration ON coupons(expiration_date);
CREATE INDEX idx_coupons_merchant ON coupons(merchant_name);
CREATE INDEX idx_coupon_locations_user_id ON coupon_locations(user_id);
CREATE INDEX idx_coupon_locations_coupon_id ON coupon_locations(coupon_id);
CREATE INDEX idx_memberships_user_id ON memberships(user_id);
CREATE INDEX idx_memberships_renewal ON memberships(renewal_date);
CREATE INDEX idx_membership_locations_user_id ON membership_locations(user_id);
```

## Step 4: Enable Row Level Security (RLS)

```sql
-- Enable RLS on all tables
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE coupons ENABLE ROW LEVEL SECURITY;
ALTER TABLE coupon_locations ENABLE ROW LEVEL SECURITY;
ALTER TABLE memberships ENABLE ROW LEVEL SECURITY;
ALTER TABLE membership_locations ENABLE ROW LEVEL SECURITY;

-- Users can only access their own data
CREATE POLICY "Users can access own data"
    ON users FOR SELECT USING (user_id = current_user_id());

CREATE POLICY "Users can access own coupons"
    ON coupons FOR ALL USING (user_id = current_user_id());

CREATE POLICY "Users can access own coupon locations"
    ON coupon_locations FOR ALL USING (user_id = current_user_id());

CREATE POLICY "Users can access own memberships"
    ON memberships FOR ALL USING (user_id = current_user_id());

CREATE POLICY "Users can access own membership locations"
    ON membership_locations FOR ALL USING (user_id = current_user_id());
```

## Step 5: Update Android Configuration

Update `AppModule.kt` with your Supabase credentials:

```kotlin
@Singleton
@Provides
fun provideRetrofit(httpClient: OkHttpClient, gson: Gson): Retrofit {
    val baseUrl = "https://YOUR-PROJECT.supabase.co/rest/v1/"
    
    return Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .client(httpClient)
        .build()
}
```

Add Supabase URL to `OkHttpClient` headers:

```kotlin
@Singleton
@Provides
fun provideHttpClient(): OkHttpClient {
    val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    val authInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .addHeader("apikey", "YOUR-ANON-KEY")
            .addHeader("Authorization", "Bearer YOUR-ANON-KEY")
            .build()
        chain.proceed(request)
    }

    return OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .addInterceptor(authInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
}
```

## Step 6: Test Connection

```bash
# Test API endpoint
curl -X GET 'https://YOUR-PROJECT.supabase.co/rest/v1/coupons' \
  -H 'apikey: YOUR-ANON-KEY' \
  -H 'Authorization: Bearer YOUR-ANON-KEY'
```

## API Endpoints

```
GET    /rest/v1/coupons              - List all coupons
POST   /rest/v1/coupons              - Create coupon
GET    /rest/v1/coupons?id=eq.UUID   - Get specific coupon
PATCH  /rest/v1/coupons?id=eq.UUID   - Update coupon
DELETE /rest/v1/coupons?id=eq.UUID   - Delete coupon

GET    /rest/v1/memberships          - List all memberships
POST   /rest/v1/memberships          - Create membership
GET    /rest/v1/memberships?id=eq.UUID
PATCH  /rest/v1/memberships?id=eq.UUID
DELETE /rest/v1/memberships?id=eq.UUID

GET    /rest/v1/users                - List users
POST   /rest/v1/users                - Create user
GET    /rest/v1/users?id=eq.USER_ID
PATCH  /rest/v1/users?id=eq.USER_ID
DELETE /rest/v1/users?id=eq.USER_ID
```

## Query Examples

```bash
# Get user's coupons
curl -X GET 'https://YOUR-PROJECT.supabase.co/rest/v1/coupons?user_id=eq.USER_ID&order=expiration_date' \
  -H 'apikey: YOUR-ANON-KEY'

# Get expiring coupons this week
curl -X GET 'https://YOUR-PROJECT.supabase.co/rest/v1/coupons?user_id=eq.USER_ID&expiration_date=gt.NOW()&expiration_date=lt.NOW()+7days' \
  -H 'apikey: YOUR-ANON-KEY'

# Search coupons by merchant
curl -X GET 'https://YOUR-PROJECT.supabase.co/rest/v1/coupons?user_id=eq.USER_ID&merchant_name=ilike.*TARGET*' \
  -H 'apikey: YOUR-ANON-KEY'
```

## Troubleshooting

- **CORS errors**: Check Supabase project settings for CORS configuration
- **RLS policy errors**: Ensure `current_user_id()` matches your user ID column
- **Connection timeout**: Verify internet connectivity and firewall rules
- **401 Unauthorized**: Check API key is correct and not rotated
