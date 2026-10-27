<?php

/**
 * Creates the EMPTY database for the isolated review environment.
 *
 * Mirror of the repo's own `_site_patch_run.php` convention: a standalone,
 * guarded script that refuses to act unless everything lines up.
 *
 * Guards, in order:
 *   1. The target name must be given on the command line.
 *   2. It must be on the allow-list for this script and NOT on the deny-list of
 *      existing business databases.
 *   3. It must not already exist.
 *
 * It connects on a throwaway connection with NO default schema, so nothing is
 * ever selected from an existing database, and it creates the database only.
 * No tables, no data, no migrations -- those are the caller's job.
 *
 * Usage:  php _review_db.php cashbookbd_review
 */

const ALLOWED_PREFIX = 'cashbookbd_review';

// Databases this script must never touch. Kept explicit so the guard is
// readable rather than clever.
const DENY = [
    'krishibitandatabase',
    'myappsaasdatabase',
    'cashbookbd',
    'cashbook_api',
    'information_schema',
    'mysql',
    'performance_schema',
    'sys',
];

$target = $argv[1] ?? '';

if ($target === '') {
    fwrite(STDERR, "STOPPED: no database name given.\nUsage: php _review_db.php cashbookbd_review\n");
    exit(1);
}

if (!str_starts_with($target, ALLOWED_PREFIX)) {
    fwrite(STDERR, "STOPPED: '{$target}' does not start with '" . ALLOWED_PREFIX . "'. This script only creates review databases.\n");
    exit(1);
}

if (in_array($target, DENY, true)) {
    fwrite(STDERR, "STOPPED: '{$target}' is an existing business database and is refused by name.\n");
    exit(1);
}

require __DIR__ . '/vendor/autoload.php';
$app = require __DIR__ . '/bootstrap/app.php';
$app->make(Illuminate\Contracts\Console\Kernel::class)->bootstrap();

use Illuminate\Support\Facades\DB;

// A connection with no default schema: the point is to reach the server without
// selecting, or depending on, any existing database.
$base = config('database.connections.mysql');
$base['database'] = null;          // no schema selected
config(['database.connections.review_create' => $base]);

$conn = DB::connection('review_create');
$server = $conn->getConfig('host') . ':' . $conn->getConfig('port');

$exists = $conn->selectOne(
    'select schema_name from information_schema.schemata where schema_name = ?',
    [$target]
);

if ($exists) {
    fwrite(STDERR, "STOPPED: database '{$target}' already exists. Refusing to touch it -- drop it yourself if you meant to start over.\n");
    exit(1);
}

// Identifier is interpolated because DDL cannot bind it; the allow-list above is
// what makes that safe.
$conn->statement("create database `{$target}` character set utf8mb4 collate utf8mb4_unicode_ci");

echo "created database : {$target}\n";
echo "server           : {$server}\n";
echo "tables           : 0 (empty -- run migrations against it next)\n";
