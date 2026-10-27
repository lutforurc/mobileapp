<?php

/**
 * Creates a FICTIONAL company in the isolated review database.
 *
 * It does not hand-write a tenant. It drives the product's own registration
 * provisioning: the row the OTP step would have written is inserted with a known
 * code, and then `POST /api/register/verify-otp` is dispatched through the real
 * HTTP kernel -- same routes, same middleware, same controller -- so the company
 * is built exactly the way a self-registered customer's is (company, branch,
 * inter-branch head, day-close, chart of accounts, roles, admin user).
 *
 * Why the OTP row is injected: `SmsService` refuses to send when SMS is disabled,
 * and the real code is stored hashed, so the code cannot be read back. Injecting
 * a known one is the only way to reach the provisioning without a live SMS
 * channel -- and it is harmless here because this database contains nothing but
 * synthetic data.
 *
 * Every value below is invented. No real customer, company, product or voucher
 * data is read, copied or referenced.
 *
 * Guard: refuses to run unless the connection's database starts with
 * `cashbookbd_review`.
 *
 * Usage:  php _review_seed.php
 */

const ALLOWED_PREFIX = 'cashbookbd_review';

// ---------------------------------------------------------------- fictional
$review = [
    'company_name' => 'Sample Trading & Supplies',
    'branch_name'  => 'Sample Trading & Supplies',
    'address'      => '12 Example Road, Dhaka 1205',
    'user_name'    => 'Review User',
    'email'        => 'review.user@example.invalid',
    'mobile'       => '01711000001',
    'password'     => 'Review!Pass123',
    'otp'          => '123456',
    'notes'        => 'Synthetic review tenant - no real data',
];

require __DIR__ . '/vendor/autoload.php';
$app = require __DIR__ . '/bootstrap/app.php';
$app->make(Illuminate\Contracts\Console\Kernel::class)->bootstrap();

use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Facades\Schema;
use Illuminate\Support\Str;

// ------------------------------------------------------------------- guard
$db = DB::connection()->getDatabaseName();
if (!str_starts_with($db, ALLOWED_PREFIX)) {
    fwrite(STDERR, "STOPPED: connected database is '{$db}', which is not a review database (must start with '" . ALLOWED_PREFIX . "').\n");
    exit(1);
}
echo "database : {$db}\n";

if (DB::table('com_companies')->where('name', $review['company_name'])->exists()) {
    fwrite(STDERR, "STOPPED: '{$review['company_name']}' already exists here. Drop the review database and re-run setup to start over.\n");
    exit(1);
}

// ---------------------------------------------- 1. the OTP row requestOtp would write
$sessionId = (string) Str::uuid();
$columns   = array_column(DB::select('show columns from registration_otps'), 'Field');

$row = [
    'otp_session_id'  => $sessionId,
    'mobile'          => $review['mobile'],
    'otp_hash'        => Hash::make($review['otp']),
    'attempts'        => 0,
    'expires_at'      => now()->addMinutes(30),
    'company_name'    => $review['company_name'],
    'company_address' => $review['address'],
    'branch_name'     => $review['branch_name'],
    'branch_address'  => $review['address'],
    'user_name'       => $review['user_name'],
    'contact_person'  => $review['user_name'],
    'password_hash'   => Hash::make($review['password']),
    'notes'           => $review['notes'],
    'created_at'      => now(),
    'updated_at'      => now(),
];
// Column names have moved between revisions, so fill whatever exists.
foreach (['email', 'user_email'] as $emailColumn) {
    if (in_array($emailColumn, $columns, true)) { $row[$emailColumn] = $review['email']; }
}
$row = array_intersect_key($row, array_flip($columns));

DB::table('registration_otps')->insert($row);
echo "otp row  : inserted ({$sessionId})\n";

// ------------------------------------------- 2. drive the real provisioning route
$request = Illuminate\Http\Request::create('/api/register/verify-otp', 'POST', [
    'otp_session_id' => $sessionId,
    'mobile'         => $review['mobile'],
    'otp'            => $review['otp'],
]);

$response = $app->make(Illuminate\Contracts\Http\Kernel::class)->handle($request);
echo "verify   : HTTP " . $response->getStatusCode() . "\n";
echo "           " . trim($response->getContent()) . "\n";

// ------------------------------------------------------------- 3. what exists now
$company = DB::table('com_companies')->where('name', $review['company_name'])->first();
if (!$company) {
    fwrite(STDERR, "\nSTOPPED: the company was not created - read the verify response above.\n");
    exit(1);
}

$branch = DB::table('com_branches')->where('company_id', $company->id)->first();

echo "\nprovisioned by the product's own registration path:\n";
echo "  company : #{$company->id}  {$company->name}\n";
echo "  branch  : " . ($branch ? "#{$branch->id}  {$branch->name}" : '(none)') . "\n";

$counts = [
    'coa_level1s'   => ['company_id', $company->id],
    'coa_level2s'   => ['company_id', $company->id],
    'coa_level3s'   => ['company_id', $company->id],
    'coa_level4s'   => ['company_id', $company->id],
    'units'         => ['company_id', $company->id],
    'usr_roles'     => ['company_id', $company->id],
];
foreach ($counts as $table => [$column, $value]) {
    if (!Schema::hasTable($table)) { continue; }
    echo sprintf("  %-14s %d rows\n", $table, DB::table($table)->where($column, $value)->count());
}

$userCount = Schema::hasTable('users')
    ? DB::table('users')->where('company_id', $company->id)->count() : 0;
echo sprintf("  %-14s %d rows\n", 'users', $userCount);

echo "\nsign-in for this synthetic tenant (isolated database only):\n";
echo "  identifier : {$review['email']}   (or {$review['mobile']})\n";
echo "  password   : {$review['password']}\n";
echo "\nNext: add fictional products/customers through the app's own API, and see\n";
echo "tools/review-env/README.md for what each piece is for.\n";
